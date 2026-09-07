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

    private Payment getPayment(UUID id) {
        return paymentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found: " + id));
    }
}
