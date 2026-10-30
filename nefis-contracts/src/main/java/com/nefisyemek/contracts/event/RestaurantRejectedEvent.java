package com.nefisyemek.contracts.event;

import java.time.Instant;
import java.util.UUID;

public record RestaurantRejectedEvent(
        String eventId,
        UUID sagaId,
        UUID orderId,
        UUID restaurantId,
        String reason,
        Instant occurredAt
) {
    public static RestaurantRejectedEvent of(UUID orderId, UUID restaurantId, String reason) {
        return new RestaurantRejectedEvent(
                UUID.randomUUID().toString(),
                orderId,
                orderId,
                restaurantId,
                reason,
                Instant.now()
        );
    }
}
