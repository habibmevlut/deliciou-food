package com.nefisyemek.contracts.event;

import java.time.Instant;
import java.util.UUID;

public record PaymentFailedEvent(
        String eventId,
        UUID sagaId,
        UUID orderId,
        UUID paymentId,
        String reason,
        Instant occurredAt
) {
    public static PaymentFailedEvent of(UUID orderId, UUID paymentId, String reason) {
        return new PaymentFailedEvent(
                UUID.randomUUID().toString(),
                orderId,
                orderId,
                paymentId,
                reason,
                Instant.now()
        );
    }
}
