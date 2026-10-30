package com.nefisyemek.payment;

import com.nefisyemek.contracts.Topics;
import com.nefisyemek.contracts.command.ChargePaymentCommand;
import com.nefisyemek.payment.controller.dto.PaymentResponse;
import com.nefisyemek.payment.controller.dto.ProcessedEventResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Aynı ChargePaymentCommand iki kez Kafka'ya giderse gerçek Postgres'te tek tahsilat kalır.
 * Unit test mock'lar; bu sınıf processed_events unique + listener deserialize'ı görür.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
@EnabledIf("dockerAvailable")
@TestPropertySource(properties = {
        "spring.kafka.consumer.auto-offset-reset=earliest",
        "management.tracing.sampling.probability=0",
        "management.zipkin.tracing.endpoint=http://127.0.0.1:9"
})
class PaymentIdempotencyIT {

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

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Test
    void duplicateChargeCommandIsChargedOnce() {
        UUID orderId = UUID.randomUUID();
        ChargePaymentCommand command = ChargePaymentCommand.of(
                orderId, UUID.randomUUID(), new BigDecimal("149.90"));

        kafkaTemplate.send(Topics.PAYMENT_COMMANDS, orderId.toString(), command);
        kafkaTemplate.send(Topics.PAYMENT_COMMANDS, orderId.toString(), command);
        kafkaTemplate.flush();

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            List<ProcessedEventResponse> processed = rest.exchange(
                    "/api/payments/processed-events",
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<List<ProcessedEventResponse>>() {
                    }).getBody();
            assertThat(processed)
                    .isNotNull()
                    .filteredOn(row -> orderId.equals(row.orderId()))
                    .hasSize(1)
                    .first()
                    .extracting(ProcessedEventResponse::eventId)
                    .isEqualTo(command.commandId());

            List<PaymentResponse> payments = rest.exchange(
                    "/api/payments",
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<List<PaymentResponse>>() {
                    }).getBody();
            assertThat(payments)
                    .isNotNull()
                    .filteredOn(row -> orderId.equals(row.orderId()))
                    .hasSize(1);
        });
    }
}
