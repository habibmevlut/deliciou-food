package com.nefisyemek.restaurant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sözleşmenin üretici tarafı: gerçek restaurant-service JSON'u bu alanları verir.
 * Kapı tarafı aynı alanları WireMock ile doğrular ({@code GatewayRestaurantContractIT}).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
@EnabledIf("dockerAvailable")
@TestPropertySource(properties = {
        "nefis.kafka.enabled=false",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration",
        "management.tracing.sampling.probability=0",
        "management.zipkin.tracing.endpoint=http://127.0.0.1:9"
})
class RestaurantPostgresIT {

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

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void getRestaurantJsonHasContractFields() throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", "Adana Sofrası");
        body.put("address", "Kadıköy");
        body.put("cuisine", "Türk");
        body.put("open", true);
        ResponseEntity<String> created = rest.postForEntity("/api/restaurants", body, String.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        JsonNode createdJson = objectMapper.readTree(created.getBody());
        UUID id = UUID.fromString(createdJson.get("id").asText());

        JsonNode got = objectMapper.readTree(rest.getForObject("/api/restaurants/" + id, String.class));
        assertRestaurantContract(got);
        assertThat(got.get("name").asText()).isEqualTo("Adana Sofrası");
        assertThat(got.get("open").asBoolean()).isTrue();
    }

    static void assertRestaurantContract(JsonNode node) {
        assertThat(node.get("id").asText()).isNotBlank();
        assertThat(node.get("name").asText()).isNotBlank();
        assertThat(node.get("address").asText()).isNotBlank();
        assertThat(node.get("cuisine").asText()).isNotBlank();
        assertThat(node.has("open")).isTrue();
        assertThat(node.has("createdAt")).isTrue();
        assertThat(node.has("updatedAt")).isTrue();
    }
}
