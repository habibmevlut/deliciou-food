package com.nefisyemek.order.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nefisyemek.order.domain.OutboxMessage;
import com.nefisyemek.order.repository.OutboxMessageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Polling publisher: unpublished outbox satırını Kafka'ya gönderir, sonra published_at yazar.
 *
 * Kafka ACK gelip published_at COMMIT edilmeden process ölürse aynı komut tekrar gider.
 * Asıl idempotent consumer Milestone 3.
 */
@Component
@ConditionalOnProperty(name = "nefis.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);
    private static final String TRUSTED_PREFIX = "com.nefisyemek.contracts.";

    private final OutboxMessageRepository outbox;
    private final KafkaTemplate<String, Object> kafka;
    private final ObjectMapper objectMapper;
    private final int batchSize;

    public OutboxPublisher(
            OutboxMessageRepository outbox,
            KafkaTemplate<String, Object> kafka,
            ObjectMapper objectMapper,
            @Value("${nefis.outbox.batch-size:50}") int batchSize
    ) {
        this.outbox = outbox;
        this.kafka = kafka;
        this.objectMapper = objectMapper;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${nefis.outbox.poll-ms:250}")
    @Transactional
    public void publishBatch() {
        List<OutboxMessage> batch = outbox.lockUnpublished(batchSize);
        if (batch.isEmpty()) {
            return;
        }
        for (OutboxMessage message : batch) {
            publishOne(message);
        }
    }

    private void publishOne(OutboxMessage message) {
        try {
            Object payload = deserialize(message);
            kafka.send(message.getTopic(), message.getMessageKey(), payload)
                    .get(5, TimeUnit.SECONDS);
            message.markPublished();
            log.info("OUTBOX published id={} topic={} key={}",
                    message.getId(), message.getTopic(), message.getMessageKey());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            message.recordFailure("interrupted");
            log.warn("OUTBOX publish interrupted id={} topic={}", message.getId(), message.getTopic());
        } catch (Exception e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            String detail = cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
            message.recordFailure(detail);
            log.warn("OUTBOX publish failed id={} topic={} attempts={}: {}",
                    message.getId(), message.getTopic(), message.getAttempts(), detail);
        }
    }

    private Object deserialize(OutboxMessage message) throws Exception {
        String typeName = message.getPayloadType();
        if (typeName == null || !typeName.startsWith(TRUSTED_PREFIX)) {
            throw new IllegalArgumentException("untrusted payload type: " + typeName);
        }
        Class<?> type = Class.forName(typeName);
        return objectMapper.readValue(message.getPayload(), type);
    }
}
