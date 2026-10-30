package com.nefisyemek.order.repository;

import com.nefisyemek.order.domain.OutboxMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface OutboxMessageRepository extends JpaRepository<OutboxMessage, UUID> {

    /**
     * SKIP LOCKED: ikinci bir order-service instance aynı satırı çekmez.
     * Lab'de tek instance var; kilit yine de "neden outbox poller böyle yazar" dersidir.
     */
    @Query(value = """
            SELECT * FROM outbox_messages
            WHERE published_at IS NULL
            ORDER BY created_at ASC
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OutboxMessage> lockUnpublished(@Param("limit") int limit);

    List<OutboxMessage> findTop50ByOrderByCreatedAtDesc();

    List<OutboxMessage> findTop50ByPublishedAtIsNullOrderByCreatedAtAsc();
}
