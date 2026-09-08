package com.nefisyemek.contracts.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
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
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

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
        JsonDeserializer<Object> deserializer = new JsonDeserializer<>(Object.class, objectMapper, true);
        deserializer.addTrustedPackages("com.nefisyemek.contracts");
        deserializer.setUseTypeHeaders(true);
        deserializer.setRemoveTypeHeaders(false);
        return new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(), deserializer);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory(
            ConsumerFactory<String, Object> kafkaConsumerFactory
    ) {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(kafkaConsumerFactory);
        factory.setConcurrency(1);
        return factory;
    }
}
