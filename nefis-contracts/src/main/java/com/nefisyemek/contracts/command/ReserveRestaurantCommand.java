package com.nefisyemek.contracts.command;

import java.time.Instant;
import java.util.UUID;

public record ReserveRestaurantCommand(
        String commandId,
        UUID sagaId,
        UUID orderId,
        UUID restaurantId,
        boolean forceReject,
        boolean forceAccept,
        Instant createdAt
) {
    public static ReserveRestaurantCommand of(
            UUID orderId,
            UUID restaurantId,
            boolean forceReject,
            boolean forceAccept
    ) {
        return new ReserveRestaurantCommand(
                UUID.randomUUID().toString(),
                orderId,
                orderId,
                restaurantId,
                forceReject,
                forceAccept,
                Instant.now()
        );
    }
}
