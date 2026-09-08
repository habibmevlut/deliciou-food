package com.nefisyemek.payment.service;

import com.nefisyemek.payment.controller.dto.CreatePaymentRequest;
import com.nefisyemek.payment.controller.dto.PaymentResponse;
import com.nefisyemek.payment.domain.Payment;
import com.nefisyemek.payment.domain.PaymentStatus;
import com.nefisyemek.payment.exception.ResourceNotFoundException;
import com.nefisyemek.payment.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        paymentService = new PaymentService(paymentRepository);
    }

    @Test
    void createDefaultsStatusToPending() {
        UUID orderId = UUID.randomUUID();
        CreatePaymentRequest request = new CreatePaymentRequest(orderId, new BigDecimal("99.50"), null);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PaymentResponse response = paymentService.create(request);

        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(response.orderId()).isEqualTo(orderId);
        assertThat(response.amount()).isEqualByComparingTo("99.50");
    }

    @Test
    void findByIdThrowsWhenMissing() {
        UUID id = UUID.randomUUID();
        when(paymentRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.findById(id))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(id.toString());
    }

    @Test
    void captureForSagaWritesCaptured() {
        UUID orderId = UUID.randomUUID();
        when(paymentRepository.findFirstByOrderIdOrderByCreatedAtDesc(orderId)).thenReturn(Optional.empty());
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Payment payment = paymentService.captureForSaga(orderId, new BigDecimal("40.00"));

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CAPTURED);
    }

    @Test
    void captureForSagaIsIdempotentWhenAlreadyCaptured() {
        UUID orderId = UUID.randomUUID();
        Payment existing = new Payment(orderId, new BigDecimal("40.00"), PaymentStatus.CAPTURED);
        when(paymentRepository.findFirstByOrderIdOrderByCreatedAtDesc(orderId)).thenReturn(Optional.of(existing));

        Payment payment = paymentService.captureForSaga(orderId, new BigDecimal("40.00"));

        assertThat(payment).isSameAs(existing);
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void refundForSagaMarksCapturedAsRefunded() {
        UUID orderId = UUID.randomUUID();
        Payment existing = new Payment(orderId, new BigDecimal("40.00"), PaymentStatus.CAPTURED);
        when(paymentRepository.findFirstByOrderIdOrderByCreatedAtDesc(orderId)).thenReturn(Optional.of(existing));
        when(paymentRepository.save(existing)).thenReturn(existing);

        Payment payment = paymentService.refundForSaga(orderId);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
    }
}
