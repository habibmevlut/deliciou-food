package com.nefisyemek.order.event;

import com.nefisyemek.order.domain.OrderHistoryEntry;
import com.nefisyemek.order.domain.OrderHistoryStep;
import com.nefisyemek.order.repository.OrderHistoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * CQRS okuma modeli yazıcısı. orders tablosuna dokunmaz; yalnızca film şeridine satır ekler.
 * Aynı eventId ikinci kez gelirse sessizce geçer (idempotent projector).
 */
@Service
public class OrderHistoryRecorder {

    private static final Logger log = LoggerFactory.getLogger(OrderHistoryRecorder.class);

    private final OrderHistoryRepository history;

    public OrderHistoryRecorder(OrderHistoryRepository history) {
        this.history = history;
    }

    public void record(UUID orderId, OrderHistoryStep step, String detail, String eventId) {
        String id = eventId == null || eventId.isBlank() ? UUID.randomUUID().toString() : eventId;
        if (history.existsByEventId(id)) {
            log.info("HISTORY skip duplicate eventId={} step={}", id, step);
            return;
        }
        history.save(new OrderHistoryEntry(orderId, id, step, detail));
        log.info("HISTORY order={} step={} detail={}", orderId, step, detail);
    }
}
