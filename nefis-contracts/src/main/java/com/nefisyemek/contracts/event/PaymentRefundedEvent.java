package com.nefisyemek.contracts.event;

import java.time.Instant;
import java.util.UUID;

public record PaymentRefundedEvent(
        String eventId,
        UUID sagaId,
        UUID orderId,
        UUID paymentId,
        String reason,
        Instant occurredAt
) {
    public static PaymentRefundedEvent of(UUID orderId, UUID paymentId, String reason) {
        return new PaymentRefundedEvent(
                UUID.randomUUID().toString(),
                orderId,
                orderId,
                paymentId,
                reason,
                Instant.now()
        );
    }
}
