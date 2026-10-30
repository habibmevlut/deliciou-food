package com.nefisyemek.contracts.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentChargedEvent(
        String eventId,
        UUID sagaId,
        UUID orderId,
        UUID paymentId,
        BigDecimal amount,
        Instant occurredAt
) {
    public static PaymentChargedEvent of(UUID orderId, UUID paymentId, BigDecimal amount) {
        return new PaymentChargedEvent(
                UUID.randomUUID().toString(),
                orderId,
                orderId,
                paymentId,
                amount,
                Instant.now()
        );
    }
}
