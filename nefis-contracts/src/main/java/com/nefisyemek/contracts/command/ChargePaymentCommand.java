package com.nefisyemek.contracts.command;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ChargePaymentCommand(
        String commandId,
        UUID sagaId,
        UUID orderId,
        UUID customerId,
        BigDecimal amount,
        Instant createdAt
) {
    public static ChargePaymentCommand of(UUID orderId, UUID customerId, BigDecimal amount) {
        return new ChargePaymentCommand(
                UUID.randomUUID().toString(),
                orderId,
                orderId,
                customerId,
                amount,
                Instant.now()
        );
    }
}
