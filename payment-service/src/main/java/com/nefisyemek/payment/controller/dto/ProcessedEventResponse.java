package com.nefisyemek.payment.controller.dto;

import com.nefisyemek.payment.domain.ProcessedEvent;

import java.time.Instant;
import java.util.UUID;

public record ProcessedEventResponse(
        String eventId,
        String eventType,
        UUID orderId,
        Instant processedAt
) {
    public static ProcessedEventResponse from(ProcessedEvent event) {
        return new ProcessedEventResponse(
                event.getEventId(),
                event.getEventType(),
                event.getOrderId(),
                event.getProcessedAt()
        );
    }
}
