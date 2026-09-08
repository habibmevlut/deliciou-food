package com.nefisyemek.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.RouteLocator;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApiGatewayApplicationTest {

    @Autowired
    private RouteLocator routeLocator;

    @Test
    void definesSixBackendRoutes() {
        long count = routeLocator.getRoutes().filter(route ->
                route.getId() != null && route.getId().endsWith("-service")).count().block();
        assertThat(count).isEqualTo(6);
    }
}
