package com.nefisyemek.order.repository;

import com.nefisyemek.order.domain.OrderHistoryEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OrderHistoryRepository extends JpaRepository<OrderHistoryEntry, UUID> {

    List<OrderHistoryEntry> findByOrderIdOrderByOccurredAtAsc(UUID orderId);

    boolean existsByEventId(String eventId);
}
