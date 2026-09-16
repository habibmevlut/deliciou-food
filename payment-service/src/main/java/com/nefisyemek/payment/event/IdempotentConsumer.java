package com.nefisyemek.payment.event;

import com.nefisyemek.payment.domain.ProcessedEvent;
import com.nefisyemek.payment.repository.ProcessedEventRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Inbox: bu mesajı daha önce işledim mi?
 * Aynı commandId ikinci kez gelirse false döner — tahsilat / event tekrarlanmaz.
 */
@Service
public class IdempotentConsumer {

    private final ProcessedEventRepository processed;

    public IdempotentConsumer(ProcessedEventRepository processed) {
        this.processed = processed;
    }

    @Transactional
    public boolean claim(String eventId, String eventType, UUID orderId) {
        if (eventId == null || eventId.isBlank()) {
            return true;
        }
        if (processed.existsById(eventId)) {
            return false;
        }
        try {
            processed.saveAndFlush(new ProcessedEvent(eventId, eventType, orderId));
            return true;
        } catch (DataIntegrityViolationException ex) {
            return false;
        }
    }
}
