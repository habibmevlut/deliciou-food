package com.nefisyemek.order.controller.dto;

import com.nefisyemek.order.domain.OrderStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateOrderRequest(
        @NotNull UUID customerId,
        @NotNull UUID restaurantId,
        @NotNull @Positive BigDecimal totalAmount,
        OrderStatus status
) {
}
