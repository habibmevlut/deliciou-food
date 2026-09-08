package com.nefisyemek.notification.domain;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notifications")
public class Notification {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(nullable = false, length = 160)
    private String recipient;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private NotificationChannel channel;
    @Column(nullable = false, length = 500)
    private String message;
    @Column(name = "sent_at", nullable = false)
    private Instant sentAt;
    protected Notification() {}
    public Notification(String recipient, NotificationChannel channel, String message) {
        this.recipient = recipient; this.channel = channel; this.message = message;
    }
    @PrePersist void onCreate() { this.sentAt = Instant.now(); }
    public UUID getId() { return id; }
    public String getRecipient() { return recipient; }
    public NotificationChannel getChannel() { return channel; }
    public String getMessage() { return message; }
    public Instant getSentAt() { return sentAt; }
}
