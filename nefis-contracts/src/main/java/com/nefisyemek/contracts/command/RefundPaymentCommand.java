package com.nefisyemek.contracts.command;

import java.time.Instant;
import java.util.UUID;

public record RefundPaymentCommand(
        String commandId,
        UUID sagaId,
        UUID orderId,
        String reason,
        Instant createdAt
) {
    public static RefundPaymentCommand of(UUID orderId, String reason) {
        return new RefundPaymentCommand(
                UUID.randomUUID().toString(),
                orderId,
                orderId,
                reason,
                Instant.now()
        );
    }
}
