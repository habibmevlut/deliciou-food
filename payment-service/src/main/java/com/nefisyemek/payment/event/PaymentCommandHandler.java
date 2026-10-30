package com.nefisyemek.payment.event;

import com.nefisyemek.contracts.Topics;
import com.nefisyemek.contracts.command.ChargePaymentCommand;
import com.nefisyemek.contracts.command.RefundPaymentCommand;
import com.nefisyemek.contracts.event.PaymentChargedEvent;
import com.nefisyemek.contracts.event.PaymentFailedEvent;
import com.nefisyemek.contracts.event.PaymentRefundedEvent;
import com.nefisyemek.contracts.kafka.KafkaPayloads;
import com.nefisyemek.payment.domain.Payment;
import com.nefisyemek.payment.domain.PaymentStatus;
import com.nefisyemek.payment.service.PaymentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(name = "nefis.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class PaymentCommandHandler {

    private static final Logger log = LoggerFactory.getLogger(PaymentCommandHandler.class);

    private final PaymentService payments;
    private final IdempotentConsumer inbox;
    private final KafkaTemplate<String, Object> kafka;

    public PaymentCommandHandler(
            PaymentService payments,
            IdempotentConsumer inbox,
            KafkaTemplate<String, Object> kafka
    ) {
        this.payments = payments;
        this.inbox = inbox;
        this.kafka = kafka;
    }

    @KafkaListener(topics = Topics.PAYMENT_COMMANDS, groupId = "${spring.kafka.consumer.group-id}")
    @Transactional
    public void onCommand(@Payload Object incoming) {
        Object command = KafkaPayloads.unwrap(incoming);
        log.info("PAYMENT command type={}", command == null ? "null" : command.getClass().getName());
        switch (command) {
            case ChargePaymentCommand chargeCmd -> charge(chargeCmd);
            case RefundPaymentCommand refundCmd -> refund(refundCmd);
            case null -> log.warn("PAYMENT command null — deserialize başarısız (ErrorHandlingDeserializer)");
            default -> log.warn("PAYMENT ignored command type={}", command.getClass().getName());
        }
    }

    private void charge(ChargePaymentCommand command) {
        if (!inbox.claim(command.commandId(), "ChargePaymentCommand", command.orderId())) {
            log.info("PAYMENT idempotent skip charge commandId={} order={}",
                    command.commandId(), command.orderId());
            return;
        }
        Payment payment = payments.captureForSaga(command.orderId(), command.amount());
        if (payment.getStatus() == PaymentStatus.FAILED) {
            kafka.send(Topics.PAYMENT_EVENTS, command.orderId().toString(),
                    PaymentFailedEvent.of(command.orderId(), payment.getId(), "INVALID_AMOUNT"));
            log.warn("PAYMENT FAILED order={} payment={}", command.orderId(), payment.getId());
            return;
        }
        kafka.send(Topics.PAYMENT_EVENTS, command.orderId().toString(),
                PaymentChargedEvent.of(command.orderId(), payment.getId(), payment.getAmount()));
        log.info("PAYMENT CAPTURED order={} payment={} amount={}",
                command.orderId(), payment.getId(), payment.getAmount());
    }

    private void refund(RefundPaymentCommand command) {
        if (!inbox.claim(command.commandId(), "RefundPaymentCommand", command.orderId())) {
            log.info("PAYMENT idempotent skip refund commandId={} order={}",
                    command.commandId(), command.orderId());
            return;
        }
        Payment payment = payments.refundForSaga(command.orderId());
        if (payment == null) {
            log.warn("PAYMENT REFUND skipped — kayıt yok order={}", command.orderId());
            return;
        }
        if (payment.getStatus() != PaymentStatus.REFUNDED) {
            log.warn("PAYMENT REFUND skipped order={} status={}", command.orderId(), payment.getStatus());
            return;
        }
        kafka.send(Topics.PAYMENT_EVENTS, command.orderId().toString(),
                PaymentRefundedEvent.of(command.orderId(), payment.getId(), command.reason()));
        log.warn("PAYMENT REFUNDED order={} payment={} reason={}",
                command.orderId(), payment.getId(), command.reason());
    }
}
