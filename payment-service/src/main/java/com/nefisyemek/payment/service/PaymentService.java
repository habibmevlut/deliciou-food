package com.nefisyemek.payment.service;

import com.nefisyemek.payment.controller.dto.CreatePaymentRequest;
import com.nefisyemek.payment.controller.dto.PaymentResponse;
import com.nefisyemek.payment.controller.dto.UpdatePaymentRequest;
import com.nefisyemek.payment.domain.Payment;
import com.nefisyemek.payment.domain.PaymentStatus;
import com.nefisyemek.payment.exception.ResourceNotFoundException;
import com.nefisyemek.payment.repository.PaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;

    public PaymentService(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    @Transactional
    public PaymentResponse create(CreatePaymentRequest request) {
        PaymentStatus status = request.status() == null ? PaymentStatus.PENDING : request.status();
        Payment payment = new Payment(request.orderId(), request.amount(), status);
        return PaymentResponse.from(paymentRepository.save(payment));
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> findAll() {
        return paymentRepository.findAll().stream().map(PaymentResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public PaymentResponse findById(UUID id) {
        return PaymentResponse.from(getPayment(id));
    }

    @Transactional
    public PaymentResponse update(UUID id, UpdatePaymentRequest request) {
        Payment payment = getPayment(id);
        payment.setOrderId(request.orderId());
        payment.setAmount(request.amount());
        payment.setStatus(request.status());
        Payment saved = paymentRepository.save(payment);
        paymentRepository.flush();
        return PaymentResponse.from(saved);
    }

    @Transactional
    public void delete(UUID id) {
        Payment payment = getPayment(id);
        paymentRepository.delete(payment);
    }

    /**
     * Saga tahsilatı. Aynı order için tekrar gelen (yeni commandId'li replay) komut yeni satır açmaz.
     * Aynı commandId'nin ikinci kopyası {@link com.nefisyemek.payment.event.IdempotentConsumer}'da kesilir.
     */
    @Transactional
    public Payment captureForSaga(UUID orderId, BigDecimal amount) {
        return paymentRepository.findFirstByOrderIdOrderByCreatedAtDesc(orderId)
                .filter(existing -> existing.getStatus() == PaymentStatus.CAPTURED
                        || existing.getStatus() == PaymentStatus.REFUNDED
                        || existing.getStatus() == PaymentStatus.FAILED)
                .orElseGet(() -> {
                    PaymentStatus status = amount == null || amount.signum() <= 0
                            ? PaymentStatus.FAILED
                            : PaymentStatus.CAPTURED;
                    return paymentRepository.save(new Payment(
                            orderId,
                            amount == null ? BigDecimal.ZERO : amount,
                            status
                    ));
                });
    }

    @Transactional
    public Payment refundForSaga(UUID orderId) {
        Payment payment = paymentRepository.findFirstByOrderIdOrderByCreatedAtDesc(orderId)
                .orElse(null);
        if (payment == null) {
            return null;
        }
        if (payment.getStatus() == PaymentStatus.CAPTURED) {
            payment.setStatus(PaymentStatus.REFUNDED);
            return paymentRepository.save(payment);
        }
        return payment;
    }

    private Payment getPayment(UUID id) {
        return paymentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found: " + id));
    }
}
