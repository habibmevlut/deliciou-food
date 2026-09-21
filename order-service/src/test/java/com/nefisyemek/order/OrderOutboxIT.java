package com.nefisyemek.order;

import com.nefisyemek.contracts.Topics;
import com.nefisyemek.order.controller.dto.OrderResponse;
import com.nefisyemek.order.controller.dto.OutboxMessageResponse;
import com.nefisyemek.order.domain.OrderStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Dual-write dersi gerçek DB'de: "Sipariş Ver" aynı transaction'da orders + outbox_messages yazar.
 * Kafka container broker içindir (listener ayağa kalksın); payment-service bu testte yok.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
@EnabledIf("dockerAvailable")
@TestPropertySource(properties = {
        "spring.kafka.consumer.auto-offset-reset=earliest",
        "nefis.outbox.poll-ms=200",
        "management.tracing.sampling.probability=0",
        "management.zipkin.tracing.endpoint=http://127.0.0.1:9"
})
class OrderOutboxIT {

    static boolean dockerAvailable() {
        try {
            return org.testcontainers.DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable ex) {
            return false;
        }
    }

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    @ServiceConnection
    static KafkaContainer kafka = new KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.7.1"));

    @Autowired
    private TestRestTemplate rest;

    @Test
    void siparisVerWritesOutboxInPostgres() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("customerId", UUID.randomUUID());
        body.put("restaurantId", UUID.randomUUID());
        body.put("totalAmount", new BigDecimal("149.90"));
        body.put("forceRestaurantAccept", true);
        ResponseEntity<OrderResponse> created = rest.postForEntity("/api/orders", body, OrderResponse.class);

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody()).isNotNull();
        UUID orderId = created.getBody().id();
        assertThat(created.getBody().status()).isEqualTo(OrderStatus.AWAITING_PAYMENT);

        List<OutboxMessageResponse> outbox = rest.exchange(
                "/api/orders/outbox",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<List<OutboxMessageResponse>>() {
                }).getBody();

        assertThat(outbox).isNotNull();
        assertThat(outbox)
                .anyMatch(row -> orderId.toString().equals(row.messageKey())
                        && Topics.PAYMENT_COMMANDS.equals(row.topic()));
    }
}
