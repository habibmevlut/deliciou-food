package com.nefisyemek.payment.event;

import com.nefisyemek.contracts.Topics;
import com.nefisyemek.contracts.command.ChargePaymentCommand;
import com.nefisyemek.contracts.event.PaymentChargedEvent;
import com.nefisyemek.payment.domain.Payment;
import com.nefisyemek.payment.domain.PaymentStatus;
import com.nefisyemek.payment.service.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentCommandHandlerTest {

    @Mock
    private PaymentService payments;

    @Mock
    private IdempotentConsumer inbox;

    @Mock
    private KafkaTemplate<String, Object> kafka;

    private PaymentCommandHandler handler;

    @BeforeEach
    void setUp() {
        handler = new PaymentCommandHandler(payments, inbox, kafka);
    }

    @Test
    void firstChargeCapturesAndPublishesEvent() {
        ChargePaymentCommand command = ChargePaymentCommand.of(
                UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("149.90"));
        when(inbox.claim(command.commandId(), "ChargePaymentCommand", command.orderId())).thenReturn(true);
        Payment payment = new Payment(command.orderId(), command.amount(), PaymentStatus.CAPTURED);
        ReflectionTestUtils.setField(payment, "id", UUID.randomUUID());
        when(payments.captureForSaga(command.orderId(), command.amount())).thenReturn(payment);

        handler.onCommand(command);

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(kafka).send(eq(Topics.PAYMENT_EVENTS), eq(command.orderId().toString()), payload.capture());
        assertThat(payload.getValue()).isInstanceOf(PaymentChargedEvent.class);
    }

    @Test
    void duplicateCommandIdDoesNotChargeOrPublish() {
        ChargePaymentCommand command = ChargePaymentCommand.of(
                UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("149.90"));
        when(inbox.claim(command.commandId(), "ChargePaymentCommand", command.orderId())).thenReturn(false);

        handler.onCommand(command);

        verify(payments, never()).captureForSaga(any(), any());
        verifyNoInteractions(kafka);
    }
}
