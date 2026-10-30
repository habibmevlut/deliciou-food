package com.nefisyemek.order.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nefisyemek.order.domain.OutboxMessage;
import com.nefisyemek.order.repository.OutboxMessageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Dual-write yerine: iş transaction'ı içinde Postgres'e satır at.
 * Kafka henüz yok; {@link OutboxPublisher} COMMIT'ten sonra taşır.
 */
@Service
public class OutboxEnqueuer {

    private static final Logger log = LoggerFactory.getLogger(OutboxEnqueuer.class);

    private final OutboxMessageRepository outbox;
    private final ObjectMapper objectMapper;

    public OutboxEnqueuer(OutboxMessageRepository outbox, ObjectMapper objectMapper) {
        this.outbox = outbox;
        this.objectMapper = objectMapper;
    }

    public void enqueue(String topic, String messageKey, Object payload) {
        String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Outbox JSON yazılamadı: " + payload.getClass().getName(), e);
        }
        OutboxMessage row = new OutboxMessage(topic, messageKey, payload.getClass().getName(), json);
        outbox.save(row);
        log.info("OUTBOX enqueue topic={} key={} type={}", topic, messageKey, payload.getClass().getSimpleName());
    }
}
