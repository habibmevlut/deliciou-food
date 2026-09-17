package com.nefisyemek.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class ApiGatewayApplicationTest {

    @Autowired
    private RouteLocator routeLocator;

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void definesSixBackendRoutesAndAuth() {
        var ids = routeLocator.getRoutes().mapNotNull(route -> route.getId()).collectList().block();
        assertThat(ids).contains(
                "auth",
                "customer-service",
                "restaurant-service",
                "order-service",
                "payment-service",
                "delivery-service",
                "notification-service");
    }

    @Test
    void healthIsOpenWithoutJwt() {
        webTestClient.get().uri("/actuator/health")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void loginIsOpenWithoutJwt() {
        webTestClient.post().uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"email\":\"ali@example.com\"}")
                .exchange()
                .expectStatus().value(status -> assertThat(status).isNotEqualTo(401));
    }

    @Test
    void createCustomerIsOpenWithoutJwt() {
        webTestClient.post().uri("/api/customers")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"fullName\":\"Ali\",\"email\":\"ali@example.com\",\"phone\":\"555\"}")
                .exchange()
                .expectStatus().value(status -> assertThat(status).isNotEqualTo(401));
    }

    @Test
    void apiWithoutJwtIsUnauthorized() {
        webTestClient.get().uri("/api/orders")
                .exchange()
                .expectStatus().isUnauthorized();
    }
}
