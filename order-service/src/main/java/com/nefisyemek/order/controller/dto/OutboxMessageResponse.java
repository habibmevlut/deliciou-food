package com.nefisyemek.order.controller.dto;

import com.nefisyemek.order.domain.OutboxMessage;

import java.time.Instant;
import java.util.UUID;

public record OutboxMessageResponse(
        UUID id,
        String topic,
        String messageKey,
        String payloadType,
        String payload,
        Instant createdAt,
        Instant publishedAt,
        int attempts,
        String lastError
) {
    public static OutboxMessageResponse from(OutboxMessage message) {
        return new OutboxMessageResponse(
                message.getId(),
                message.getTopic(),
                message.getMessageKey(),
                message.getPayloadType(),
                message.getPayload(),
                message.getCreatedAt(),
                message.getPublishedAt(),
                message.getAttempts(),
                message.getLastError()
        );
    }
}
