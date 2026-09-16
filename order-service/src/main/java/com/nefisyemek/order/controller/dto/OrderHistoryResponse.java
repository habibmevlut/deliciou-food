package com.nefisyemek.order.controller.dto;

import com.nefisyemek.order.domain.OrderHistoryEntry;
import com.nefisyemek.order.domain.OrderHistoryStep;

import java.time.Instant;
import java.util.UUID;

public record OrderHistoryResponse(
        UUID id,
        UUID orderId,
        String eventId,
        OrderHistoryStep step,
        String detail,
        Instant occurredAt
) {
    public static OrderHistoryResponse from(OrderHistoryEntry entry) {
        return new OrderHistoryResponse(
                entry.getId(),
                entry.getOrderId(),
                entry.getEventId(),
                entry.getStep(),
                entry.getDetail(),
                entry.getOccurredAt()
        );
    }
}
