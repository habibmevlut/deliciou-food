package com.nefisyemek.contracts.event;

import java.time.Instant;
import java.util.UUID;

public record RestaurantAcceptedEvent(
        String eventId,
        UUID sagaId,
        UUID orderId,
        UUID restaurantId,
        Instant occurredAt
) {
    public static RestaurantAcceptedEvent of(UUID orderId, UUID restaurantId) {
        return new RestaurantAcceptedEvent(
                UUID.randomUUID().toString(),
                orderId,
                orderId,
                restaurantId,
                Instant.now()
        );
    }
}
