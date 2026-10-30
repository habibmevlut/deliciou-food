package com.nefisyemek.delivery.domain;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "deliveries")
public class Delivery {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "order_id", nullable = false)
    private UUID orderId;
    @Column(name = "courier_name", nullable = false, length = 120)
    private String courierName;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private DeliveryStatus status;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    protected Delivery() {}
    public Delivery(UUID orderId, String courierName, DeliveryStatus status) {
        this.orderId = orderId; this.courierName = courierName; this.status = status;
    }
    @PrePersist void onCreate() { Instant n = Instant.now(); createdAt = n; updatedAt = n; }
    @PreUpdate void onUpdate() { updatedAt = Instant.now(); }
    public UUID getId() { return id; }
    public UUID getOrderId() { return orderId; }
    public void setOrderId(UUID orderId) { this.orderId = orderId; }
    public String getCourierName() { return courierName; }
    public void setCourierName(String courierName) { this.courierName = courierName; }
    public DeliveryStatus getStatus() { return status; }
    public void setStatus(DeliveryStatus status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
