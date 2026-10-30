package com.nefisyemek.payment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "processed_events")
public class ProcessedEvent {

    @Id
    @Column(name = "event_id", length = 64)
    private String eventId;

    @Column(name = "event_type", nullable = false, length = 128)
    private String eventType;

    @Column(name = "order_id")
    private UUID orderId;

    @Column(name = "processed_at", nullable = false, updatable = false)
    private Instant processedAt;

    protected ProcessedEvent() {
        // JPA
    }

    public ProcessedEvent(String eventId, String eventType, UUID orderId) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.orderId = orderId;
    }

    @PrePersist
    void onCreate() {
        this.processedAt = Instant.now();
    }

    public String getEventId() {
        return eventId;
    }

    public String getEventType() {
        return eventType;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }
}
