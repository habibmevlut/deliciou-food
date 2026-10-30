package com.nefisyemek.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outbox_messages")
public class OutboxMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 128)
    private String topic;

    @Column(name = "message_key", nullable = false, length = 64)
    private String messageKey;

    @Column(name = "payload_type", nullable = false, length = 256)
    private String payloadType;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "last_error", length = 1024)
    private String lastError;

    protected OutboxMessage() {
        // JPA
    }

    public OutboxMessage(String topic, String messageKey, String payloadType, String payload) {
        this.topic = topic;
        this.messageKey = messageKey;
        this.payloadType = payloadType;
        this.payload = payload;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    public void markPublished() {
        this.publishedAt = Instant.now();
        this.lastError = null;
    }

    public void recordFailure(String error) {
        this.attempts++;
        if (error == null) {
            this.lastError = null;
            return;
        }
        this.lastError = error.length() <= 1024 ? error : error.substring(0, 1024);
    }

    public UUID getId() {
        return id;
    }

    public String getTopic() {
        return topic;
    }

    public String getMessageKey() {
        return messageKey;
    }

    public String getPayloadType() {
        return payloadType;
    }

    public String getPayload() {
        return payload;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public int getAttempts() {
        return attempts;
    }

    public String getLastError() {
        return lastError;
    }
}
