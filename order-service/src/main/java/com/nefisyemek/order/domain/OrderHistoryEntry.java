package com.nefisyemek.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "order_history_view")
public class OrderHistoryEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "event_id", nullable = false, unique = true, length = 64)
    private String eventId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 64)
    private OrderHistoryStep step;

    @Column(length = 256)
    private String detail;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    protected OrderHistoryEntry() {
        // JPA
    }

    public OrderHistoryEntry(UUID orderId, String eventId, OrderHistoryStep step, String detail) {
        this.orderId = orderId;
        this.eventId = eventId;
        this.step = step;
        this.detail = detail;
    }

    @PrePersist
    void onCreate() {
        if (this.occurredAt == null) {
            this.occurredAt = Instant.now();
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public String getEventId() {
        return eventId;
    }

    public OrderHistoryStep getStep() {
        return step;
    }

    public String getDetail() {
        return detail;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
