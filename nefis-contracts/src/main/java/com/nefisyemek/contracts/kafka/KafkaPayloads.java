package com.nefisyemek.contracts.kafka;

import org.apache.kafka.clients.consumer.ConsumerRecord;

/**
 * {@code @KafkaListener void onX(Object x)} parametresi Spring Kafka'da
 * payload değil {@link ConsumerRecord} olur — Object her şeyi kabul eder.
 * {@code @Payload} veya bu unwrap şart.
 */
public final class KafkaPayloads {

    private KafkaPayloads() {
    }

    public static Object unwrap(Object incoming) {
        if (incoming instanceof ConsumerRecord<?, ?> record) {
            return record.value();
        }
        return incoming;
    }
}
