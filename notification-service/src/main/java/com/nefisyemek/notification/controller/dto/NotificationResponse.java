package com.nefisyemek.notification.controller.dto;
import com.nefisyemek.notification.domain.Notification;
import com.nefisyemek.notification.domain.NotificationChannel;
import java.time.Instant;
import java.util.UUID;
public record NotificationResponse(UUID id, String recipient, NotificationChannel channel, String message, Instant sentAt) {
    public static NotificationResponse from(Notification n) {
        return new NotificationResponse(n.getId(), n.getRecipient(), n.getChannel(), n.getMessage(), n.getSentAt());
    }
}
