package com.nefisyemek.contracts.command;

import java.time.Instant;
import java.util.UUID;

public record AssignCourierCommand(
        String commandId,
        UUID sagaId,
        UUID orderId,
        Instant createdAt
) {
    public static AssignCourierCommand of(UUID orderId) {
        return new AssignCourierCommand(
                UUID.randomUUID().toString(),
                orderId,
                orderId,
                Instant.now()
        );
    }
}
