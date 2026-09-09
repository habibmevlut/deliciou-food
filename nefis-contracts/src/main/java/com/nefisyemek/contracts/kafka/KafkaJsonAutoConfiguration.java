package com.nefisyemek.contracts.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.util.backoff.FixedBackOff;

import java.util.Map;

/**
 * Jackson ObjectMapper (JavaTime / ISO-8601) + type header ile JSON Kafka.
 * Type header sayesinde aynı topic'te Charge ve Refund gibi farklı record'lar durabilir.
 */
@AutoConfiguration
@EnableKafka
@ConditionalOnClass(KafkaTemplate.class)
@ConditionalOnProperty(name = "nefis.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class KafkaJsonAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(KafkaJsonAutoConfiguration.class);

    /**
     * JsonDeserializer paket adını birebir eşler: {@code com.nefisyemek.contracts}
     * alt paketleri ({@code .command}, {@code .event}) kapsamaz. {@code .*} şart.
     */
    static final String TRUSTED_PACKAGES = "com.nefisyemek.contracts.*";

    @Bean
    public ProducerFactory<String, Object> kafkaProducerFactory(
            KafkaProperties properties,
            ObjectMapper objectMapper,
            ObjectProvider<SslBundles> sslBundles
    ) {
        Map<String, Object> props = properties.buildProducerProperties(sslBundles.getIfAvailable());
        JsonSerializer<Object> valueSerializer = new JsonSerializer<>(objectMapper);
        valueSerializer.setAddTypeInfo(true);
        return new DefaultKafkaProducerFactory<>(props, new StringSerializer(), valueSerializer);
    }

    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate(ProducerFactory<String, Object> kafkaProducerFactory) {
        return new KafkaTemplate<>(kafkaProducerFactory);
    }

    @Bean
    public ConsumerFactory<String, Object> kafkaConsumerFactory(
            KafkaProperties properties,
            ObjectMapper objectMapper,
            ObjectProvider<SslBundles> sslBundles
    ) {
        Map<String, Object> props = properties.buildConsumerProperties(sslBundles.getIfAvailable());
        ErrorHandlingDeserializer<String> keyDeserializer =
                new ErrorHandlingDeserializer<>(new StringDeserializer());
        ErrorHandlingDeserializer<Object> valueDeserializer =
                new ErrorHandlingDeserializer<>(jsonDeserializer(objectMapper));
        return new DefaultKafkaConsumerFactory<>(props, keyDeserializer, valueDeserializer);
    }

    @Bean
    public DefaultErrorHandler kafkaErrorHandler() {
        // Deserialize hatası DefaultErrorHandler'a gelmez; ErrorHandlingDeserializer sarmalayınca
        // kayıt atlanır, consumer offset 0'da sonsuz döngüye girmez (Kafka UI'dan bozuk mesaj vs.).
        return new DefaultErrorHandler(KafkaJsonAutoConfiguration::logSkippedRecord, new FixedBackOff(0L, 0L));
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory(
            ConsumerFactory<String, Object> kafkaConsumerFactory,
            DefaultErrorHandler kafkaErrorHandler
    ) {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(kafkaConsumerFactory);
        factory.setCommonErrorHandler(kafkaErrorHandler);
        factory.setConcurrency(1);
        return factory;
    }

    static JsonDeserializer<Object> jsonDeserializer(ObjectMapper objectMapper) {
        JsonDeserializer<Object> deserializer = new JsonDeserializer<>(Object.class, objectMapper, true);
        deserializer.addTrustedPackages(TRUSTED_PACKAGES);
        deserializer.setUseTypeHeaders(true);
        deserializer.setRemoveTypeHeaders(false);
        return deserializer;
    }

    private static void logSkippedRecord(ConsumerRecord<?, ?> record, Exception exception) {
        log.warn("Kafka kayıt atlandı topic={} partition={} offset={}: {}",
                record.topic(), record.partition(), record.offset(), exception.getMessage());
    }
}
