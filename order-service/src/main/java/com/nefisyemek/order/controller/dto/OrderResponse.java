package com.nefisyemek.order.controller.dto;

import com.nefisyemek.order.domain.Order;
import com.nefisyemek.order.domain.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OrderResponse(
        UUID id,
        UUID customerId,
        UUID restaurantId,
        BigDecimal totalAmount,
        OrderStatus status,
        String cancelReason,
        Instant createdAt,
        Instant updatedAt
) {
    public static OrderResponse from(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getCustomerId(),
                order.getRestaurantId(),
                order.getTotalAmount(),
                order.getStatus(),
                order.getCancelReason(),
                order.getCreatedAt(),
                order.getUpdatedAt()
        );
    }
}
