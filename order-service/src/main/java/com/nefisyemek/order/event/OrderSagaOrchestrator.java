package com.nefisyemek.order.event;

import com.nefisyemek.contracts.Topics;
import com.nefisyemek.contracts.command.AssignCourierCommand;
import com.nefisyemek.contracts.command.ChargePaymentCommand;
import com.nefisyemek.contracts.command.RefundPaymentCommand;
import com.nefisyemek.contracts.command.ReserveRestaurantCommand;
import com.nefisyemek.contracts.event.CourierAssignedEvent;
import com.nefisyemek.contracts.event.CourierAssignmentFailedEvent;
import com.nefisyemek.contracts.event.PaymentChargedEvent;
import com.nefisyemek.contracts.event.PaymentFailedEvent;
import com.nefisyemek.contracts.event.PaymentRefundedEvent;
import com.nefisyemek.contracts.event.RestaurantAcceptedEvent;
import com.nefisyemek.contracts.event.RestaurantRejectedEvent;
import com.nefisyemek.contracts.kafka.KafkaPayloads;
import com.nefisyemek.order.domain.Order;
import com.nefisyemek.order.domain.OrderStatus;
import com.nefisyemek.order.exception.IllegalOrderStateException;
import com.nefisyemek.order.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Orchestration-based saga: adımları burası sıralar.
 * Choreography'de her servis bir sonrakini tetikler; burada tek yönetmen order-service.
 *
 * Dual-write: DB kaydı + Kafka send atomik değil. Process send'den önce çökerse
 * komut kaybolur — Milestone 2.5 Outbox bunu çözer.
 */
@Service
@ConditionalOnProperty(name = "nefis.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class OrderSagaOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(OrderSagaOrchestrator.class);

    private final OrderRepository orders;
    private final KafkaTemplate<String, Object> kafka;

    public OrderSagaOrchestrator(OrderRepository orders, KafkaTemplate<String, Object> kafka) {
        this.orders = orders;
        this.kafka = kafka;
    }

    @Transactional
    public void start(Order order) {
        order.setStatus(OrderStatus.AWAITING_PAYMENT);
        order.setCancelReason(null);
        orders.save(order);

        ChargePaymentCommand command = ChargePaymentCommand.of(
                order.getId(),
                order.getCustomerId(),
                order.getTotalAmount()
        );
        kafka.send(Topics.PAYMENT_COMMANDS, key(order.getId()), command);
        log.info("SAGA start order={} status=AWAITING_PAYMENT -> {}", order.getId(), Topics.PAYMENT_COMMANDS);
    }

    /**
     * Takılı saga: komut Kafka'da kayboldu veya consumer o an okuyamadı.
     * Status aynı kalır; yalnızca o adımın komutu yeniden yazılır (idempotent consumer varsayımı).
     */
    public void replayCurrentStep(Order order) {
        switch (order.getStatus()) {
            case AWAITING_PAYMENT -> {
                kafka.send(Topics.PAYMENT_COMMANDS, key(order.getId()), ChargePaymentCommand.of(
                        order.getId(), order.getCustomerId(), order.getTotalAmount()));
                log.info("SAGA replay order={} -> {}", order.getId(), Topics.PAYMENT_COMMANDS);
            }
            case AWAITING_RESTAURANT -> {
                kafka.send(Topics.RESTAURANT_COMMANDS, key(order.getId()), ReserveRestaurantCommand.of(
                        order.getId(),
                        order.getRestaurantId(),
                        order.isForceRestaurantReject(),
                        order.isForceRestaurantAccept()));
                log.info("SAGA replay order={} -> {}", order.getId(), Topics.RESTAURANT_COMMANDS);
            }
            case AWAITING_DELIVERY -> {
                kafka.send(Topics.DELIVERY_COMMANDS, key(order.getId()), AssignCourierCommand.of(order.getId()));
                log.info("SAGA replay order={} -> {}", order.getId(), Topics.DELIVERY_COMMANDS);
            }
            default -> throw new IllegalOrderStateException(
                    "Saga replay yalnızca AWAITING_* için. status=" + order.getStatus());
        }
    }

    @KafkaListener(topics = Topics.PAYMENT_EVENTS, groupId = "${spring.kafka.consumer.group-id}")
    @Transactional
    public void onPaymentEvent(@Payload Object incoming) {
        Object event = KafkaPayloads.unwrap(incoming);
        log.info("SAGA payment-events type={}", event == null ? "null" : event.getClass().getName());
        switch (event) {
            case PaymentChargedEvent charged -> onPaymentCharged(charged);
            case PaymentFailedEvent failed -> onPaymentFailed(failed);
            case PaymentRefundedEvent refunded ->
                    log.info("SAGA payment refunded order={} payment={}", refunded.orderId(), refunded.paymentId());
            case null -> log.warn("SAGA payment-events null — deserialize başarısız");
            default -> log.warn("SAGA ignored payment event type={}", event.getClass().getName());
        }
    }

    @KafkaListener(topics = Topics.RESTAURANT_EVENTS, groupId = "${spring.kafka.consumer.group-id}")
    @Transactional
    public void onRestaurantEvent(@Payload Object incoming) {
        Object event = KafkaPayloads.unwrap(incoming);
        switch (event) {
            case RestaurantAcceptedEvent accepted -> onRestaurantAccepted(accepted);
            case RestaurantRejectedEvent rejected -> onRestaurantRejected(rejected);
            default -> log.debug("SAGA ignored restaurant event type={}", typeName(event));
        }
    }

    @KafkaListener(topics = Topics.DELIVERY_EVENTS, groupId = "${spring.kafka.consumer.group-id}")
    @Transactional
    public void onDeliveryEvent(@Payload Object incoming) {
        Object event = KafkaPayloads.unwrap(incoming);
        switch (event) {
            case CourierAssignedEvent assigned -> onCourierAssigned(assigned);
            case CourierAssignmentFailedEvent failed -> onCourierFailed(failed);
            default -> log.debug("SAGA ignored delivery event type={}", typeName(event));
        }
    }

    private void onPaymentCharged(PaymentChargedEvent event) {
        orders.findById(event.orderId()).ifPresent(order -> {
            if (order.getStatus() != OrderStatus.AWAITING_PAYMENT) {
                log.info("SAGA skip payment-charged order={} status={}", order.getId(), order.getStatus());
                return;
            }
            order.setStatus(OrderStatus.AWAITING_RESTAURANT);
            orders.save(order);
            ReserveRestaurantCommand command = ReserveRestaurantCommand.of(
                    order.getId(),
                    order.getRestaurantId(),
                    order.isForceRestaurantReject(),
                    order.isForceRestaurantAccept()
            );
            kafka.send(Topics.RESTAURANT_COMMANDS, key(order.getId()), command);
            log.info("SAGA payment charged order={} payment={} -> {}",
                    order.getId(), event.paymentId(), Topics.RESTAURANT_COMMANDS);
        });
    }

    private void onPaymentFailed(PaymentFailedEvent event) {
        cancel(event.orderId(), "PAYMENT_FAILED", false);
    }

    private void onRestaurantAccepted(RestaurantAcceptedEvent event) {
        orders.findById(event.orderId()).ifPresent(order -> {
            if (order.getStatus() != OrderStatus.AWAITING_RESTAURANT) {
                log.info("SAGA skip restaurant-accepted order={} status={}", order.getId(), order.getStatus());
                return;
            }
            order.setStatus(OrderStatus.AWAITING_DELIVERY);
            orders.save(order);
            kafka.send(Topics.DELIVERY_COMMANDS, key(order.getId()), AssignCourierCommand.of(order.getId()));
            log.info("SAGA restaurant accepted order={} -> {}", order.getId(), Topics.DELIVERY_COMMANDS);
        });
    }

    private void onRestaurantRejected(RestaurantRejectedEvent event) {
        log.warn("SAGA COMPENSATE order={} reason={}", event.orderId(), event.reason());
        cancel(event.orderId(), event.reason() == null ? "RESTAURANT_REJECTED" : event.reason(), true);
    }

    private void onCourierAssigned(CourierAssignedEvent event) {
        orders.findById(event.orderId()).ifPresent(order -> {
            if (order.getStatus() != OrderStatus.AWAITING_DELIVERY) {
                log.info("SAGA skip courier-assigned order={} status={}", order.getId(), order.getStatus());
                return;
            }
            order.setStatus(OrderStatus.CONFIRMED);
            order.setCancelReason(null);
            orders.save(order);
            log.info("SAGA confirmed order={} delivery={} courier={}",
                    order.getId(), event.deliveryId(), event.courierName());
        });
    }

    private void onCourierFailed(CourierAssignmentFailedEvent event) {
        log.warn("SAGA COMPENSATE order={} reason={}", event.orderId(), event.reason());
        cancel(event.orderId(), event.reason() == null ? "COURIER_ASSIGNMENT_FAILED" : event.reason(), true);
    }

    private void cancel(UUID orderId, String reason, boolean refundPayment) {
        orders.findById(orderId).ifPresent(order -> {
            if (order.getStatus() == OrderStatus.CANCELLED || order.getStatus() == OrderStatus.CONFIRMED) {
                log.info("SAGA skip cancel order={} status={}", order.getId(), order.getStatus());
                return;
            }
            order.setStatus(OrderStatus.CANCELLED);
            order.setCancelReason(reason);
            orders.save(order);
            if (refundPayment) {
                kafka.send(Topics.PAYMENT_COMMANDS, key(orderId), RefundPaymentCommand.of(orderId, reason));
                log.warn("SAGA cancelled order={} reason={} -> RefundPaymentCommand", orderId, reason);
            } else {
                log.warn("SAGA cancelled order={} reason={} (iade yok)", orderId, reason);
            }
        });
    }

    private static String key(UUID orderId) {
        return orderId.toString();
    }

    private static String typeName(Object event) {
        return event == null ? "null" : event.getClass().getSimpleName();
    }
}
