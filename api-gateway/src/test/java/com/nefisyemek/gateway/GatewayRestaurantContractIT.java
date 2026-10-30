package com.nefisyemek.gateway;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

/**
 * Sözleşmenin tüketici tarafı: restaurant-service yok; WireMock "JSON böyle durur" der.
 * Gateway alanı silmeden, JWT'yi de düşürmeden geçirmeli.
 * Docker gerekmez.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class GatewayRestaurantContractIT {

    private static final String JWT_SECRET = "nefis-yemek-dev-jwt-secret-do-not-use-in-prod!";

    /**
     * Sözleşme gövdesi — {@code RestaurantPostgresIT} aynı alan adlarını üretir:
     * id, name, address, cuisine, open, createdAt, updatedAt.
     */
    private static final String RESTAURANT_JSON = """
            [{
              "id": "11111111-1111-1111-1111-111111111111",
              "name": "Adana Sofrası",
              "address": "Kadıköy",
              "cuisine": "Türk",
              "open": true,
              "createdAt": "2026-01-01T00:00:00Z",
              "updatedAt": "2026-01-01T00:00:00Z"
            }]
            """;

    private static final WireMockServer RESTAURANT = new WireMockServer(wireMockConfig().dynamicPort());

    static {
        RESTAURANT.start();
        RESTAURANT.stubFor(get(urlPathEqualTo("/api/restaurants"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(RESTAURANT_JSON)));
    }

    @DynamicPropertySource
    static void registerWireMock(DynamicPropertyRegistry registry) {
        String uri = "http://127.0.0.1:" + RESTAURANT.port();
        registry.add("RESTAURANT_SERVICE_URI", () -> uri);
        registry.add("CUSTOMER_SERVICE_URI", () -> uri);
        registry.add("ORDER_SERVICE_URI", () -> uri);
        registry.add("PAYMENT_SERVICE_URI", () -> uri);
        registry.add("DELIVERY_SERVICE_URI", () -> uri);
        registry.add("NOTIFICATION_SERVICE_URI", () -> uri);
        registry.add("nefis.jwt.secret", () -> JWT_SECRET);
        registry.add("management.tracing.sampling.probability", () -> "0");
    }

    @AfterAll
    static void stopWireMock() {
        RESTAURANT.stop();
    }

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void listRestaurantsHonorsJsonContractThroughGateway() {
        String token = TestJwts.hs256(JWT_SECRET, UUID.randomUUID());
        webTestClient.get().uri("/api/restaurants")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].id").isEqualTo("11111111-1111-1111-1111-111111111111")
                .jsonPath("$[0].name").isEqualTo("Adana Sofrası")
                .jsonPath("$[0].address").isEqualTo("Kadıköy")
                .jsonPath("$[0].cuisine").isEqualTo("Türk")
                .jsonPath("$[0].open").isEqualTo(true)
                .jsonPath("$[0].createdAt").exists()
                .jsonPath("$[0].updatedAt").exists();
    }

    @Test
    void listRestaurantsWithoutJwtIsUnauthorized() {
        webTestClient.get().uri("/api/restaurants")
                .exchange()
                .expectStatus().isUnauthorized();
    }
}
