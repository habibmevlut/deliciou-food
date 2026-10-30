package com.nefisyemek.notification.controller.dto;
import com.nefisyemek.notification.domain.NotificationChannel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
public record CreateNotificationRequest(@NotBlank String recipient, @NotNull NotificationChannel channel, @NotBlank String message) {}
