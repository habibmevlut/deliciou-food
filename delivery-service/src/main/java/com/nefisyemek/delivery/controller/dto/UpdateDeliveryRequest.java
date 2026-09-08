package com.nefisyemek.delivery.controller.dto;
import com.nefisyemek.delivery.domain.DeliveryStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
public record UpdateDeliveryRequest(@NotNull UUID orderId, @NotBlank String courierName, @NotNull DeliveryStatus status) {}
