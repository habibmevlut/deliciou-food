package com.nefisyemek.contracts.event;

import java.time.Instant;
import java.util.UUID;

public record CourierAssignedEvent(
        String eventId,
        UUID sagaId,
        UUID orderId,
        UUID deliveryId,
        String courierName,
        Instant occurredAt
) {
    public static CourierAssignedEvent of(UUID orderId, UUID deliveryId, String courierName) {
        return new CourierAssignedEvent(
                UUID.randomUUID().toString(),
                orderId,
                orderId,
                deliveryId,
                courierName,
                Instant.now()
        );
    }
}
