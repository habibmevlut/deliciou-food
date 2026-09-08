package com.nefisyemek.delivery.controller.dto;
import com.nefisyemek.delivery.domain.Delivery;
import com.nefisyemek.delivery.domain.DeliveryStatus;
import java.time.Instant;
import java.util.UUID;
public record DeliveryResponse(UUID id, UUID orderId, String courierName, DeliveryStatus status, Instant createdAt, Instant updatedAt) {
    public static DeliveryResponse from(Delivery d) {
        return new DeliveryResponse(d.getId(), d.getOrderId(), d.getCourierName(), d.getStatus(), d.getCreatedAt(), d.getUpdatedAt());
    }
}
