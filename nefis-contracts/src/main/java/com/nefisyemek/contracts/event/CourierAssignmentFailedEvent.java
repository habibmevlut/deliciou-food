package com.nefisyemek.contracts.event;

import java.time.Instant;
import java.util.UUID;

public record CourierAssignmentFailedEvent(
        String eventId,
        UUID sagaId,
        UUID orderId,
        String reason,
        Instant occurredAt
) {
    public static CourierAssignmentFailedEvent of(UUID orderId, String reason) {
        return new CourierAssignmentFailedEvent(
                UUID.randomUUID().toString(),
                orderId,
                orderId,
                reason,
                Instant.now()
        );
    }
}
