package com.nefisyemek.order.event;

import com.nefisyemek.contracts.Topics;
import com.nefisyemek.contracts.command.AssignCourierCommand;
import com.nefisyemek.contracts.command.ChargePaymentCommand;
import com.nefisyemek.contracts.command.RefundPaymentCommand;
import com.nefisyemek.contracts.command.ReserveRestaurantCommand;
import com.nefisyemek.contracts.event.CourierAssignedEvent;
import com.nefisyemek.contracts.event.PaymentChargedEvent;
import com.nefisyemek.contracts.event.PaymentFailedEvent;
import com.nefisyemek.contracts.event.RestaurantAcceptedEvent;
import com.nefisyemek.contracts.event.RestaurantRejectedEvent;
import com.nefisyemek.order.domain.Order;
import com.nefisyemek.order.domain.OrderStatus;
import com.nefisyemek.order.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderSagaOrchestratorTest {

    @Mock
    private OrderRepository orders;

    @Mock
    private KafkaTemplate<String, Object> kafka;

    private OrderSagaOrchestrator saga;

    @BeforeEach
    void setUp() {
        saga = new OrderSagaOrchestrator(orders, kafka);
    }

    @Test
    void startPublishesChargeCommand() {
        Order order = persistedOrder(OrderStatus.PENDING);

        saga.start(order);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.AWAITING_PAYMENT);
        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(kafka).send(eq(Topics.PAYMENT_COMMANDS), eq(order.getId().toString()), payload.capture());
        assertThat(payload.getValue()).isInstanceOf(ChargePaymentCommand.class);
    }

    @Test
    void paymentChargedMovesToRestaurant() {
        Order order = persistedOrder(OrderStatus.AWAITING_PAYMENT);
        order.setForceRestaurantAccept(true);
        when(orders.findById(order.getId())).thenReturn(Optional.of(order));

        saga.onPaymentEvent(PaymentChargedEvent.of(order.getId(), UUID.randomUUID(), order.getTotalAmount()));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.AWAITING_RESTAURANT);
        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(kafka).send(eq(Topics.RESTAURANT_COMMANDS), eq(order.getId().toString()), payload.capture());
        ReserveRestaurantCommand command = (ReserveRestaurantCommand) payload.getValue();
        assertThat(command.forceAccept()).isTrue();
        assertThat(command.forceReject()).isFalse();
    }

    @Test
    void paymentFailedCancelsWithoutRefund() {
        Order order = persistedOrder(OrderStatus.AWAITING_PAYMENT);
        when(orders.findById(order.getId())).thenReturn(Optional.of(order));

        saga.onPaymentEvent(PaymentFailedEvent.of(order.getId(), UUID.randomUUID(), "INVALID_AMOUNT"));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.getCancelReason()).isEqualTo("PAYMENT_FAILED");
        verifyNoInteractions(kafka);
    }

    @Test
    void restaurantAcceptedAssignsCourier() {
        Order order = persistedOrder(OrderStatus.AWAITING_RESTAURANT);
        when(orders.findById(order.getId())).thenReturn(Optional.of(order));

        saga.onRestaurantEvent(RestaurantAcceptedEvent.of(order.getId(), order.getRestaurantId()));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.AWAITING_DELIVERY);
        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(kafka).send(eq(Topics.DELIVERY_COMMANDS), eq(order.getId().toString()), payload.capture());
        assertThat(payload.getValue()).isInstanceOf(AssignCourierCommand.class);
    }

    @Test
    void restaurantRejectedCompensatesWithRefund() {
        Order order = persistedOrder(OrderStatus.AWAITING_RESTAURANT);
        when(orders.findById(order.getId())).thenReturn(Optional.of(order));

        saga.onRestaurantEvent(RestaurantRejectedEvent.of(
                order.getId(), order.getRestaurantId(), "FORCE_REJECT"));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.getCancelReason()).isEqualTo("FORCE_REJECT");
        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(kafka).send(eq(Topics.PAYMENT_COMMANDS), eq(order.getId().toString()), payload.capture());
        assertThat(payload.getValue()).isInstanceOf(RefundPaymentCommand.class);
    }

    @Test
    void courierAssignedConfirmsOrder() {
        Order order = persistedOrder(OrderStatus.AWAITING_DELIVERY);
        when(orders.findById(order.getId())).thenReturn(Optional.of(order));

        saga.onDeliveryEvent(CourierAssignedEvent.of(order.getId(), UUID.randomUUID(), "Kurye-Ada"));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        verifyNoInteractions(kafka);
    }

    @Test
    void duplicatePaymentChargedIsIgnored() {
        Order order = persistedOrder(OrderStatus.CONFIRMED);
        when(orders.findById(order.getId())).thenReturn(Optional.of(order));

        saga.onPaymentEvent(PaymentChargedEvent.of(order.getId(), UUID.randomUUID(), order.getTotalAmount()));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        verifyNoInteractions(kafka);
    }

    @Test
    void replayAwaitingPaymentResendsChargeCommand() {
        Order order = persistedOrder(OrderStatus.AWAITING_PAYMENT);

        saga.replayCurrentStep(order);

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(kafka).send(eq(Topics.PAYMENT_COMMANDS), eq(order.getId().toString()), payload.capture());
        assertThat(payload.getValue()).isInstanceOf(ChargePaymentCommand.class);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.AWAITING_PAYMENT);
    }

    private Order persistedOrder(OrderStatus status) {
        Order order = new Order(UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("149.90"), status);
        ReflectionTestUtils.setField(order, "id", UUID.randomUUID());
        return order;
    }
}
