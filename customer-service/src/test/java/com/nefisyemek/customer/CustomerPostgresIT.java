package com.nefisyemek.customer;

import com.nefisyemek.customer.controller.dto.CustomerResponse;
import com.nefisyemek.customer.controller.dto.LoginResponse;
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

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Piramit ortası: gerçek Postgres + Flyway. Mockito unique email / JWT login'i kaçırır.
 * Docker yoksa bu sınıf atlanır ({@code disabledWithoutDocker}).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
@EnabledIf("dockerAvailable")
@TestPropertySource(properties = {
        "management.tracing.sampling.probability=0",
        "management.zipkin.tracing.endpoint=http://127.0.0.1:9"
})
class CustomerPostgresIT {

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

    @Test
    void createThenLoginAgainstRealPostgres() {
        String email = "ali." + UUID.randomUUID() + "@example.com";
        ResponseEntity<CustomerResponse> created = rest.postForEntity(
                "/api/customers",
                Map.of("fullName", "Ali Veli", "email", email, "phone", "5551112233"),
                CustomerResponse.class);

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody()).isNotNull();
        assertThat(created.getBody().email()).isEqualTo(email);
        assertThat(created.getBody().id()).isNotNull();

        ResponseEntity<LoginResponse> login = rest.postForEntity(
                "/api/auth/login",
                Map.of("email", email),
                LoginResponse.class);

        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(login.getBody()).isNotNull();
        assertThat(login.getBody().accessToken()).isNotBlank();
        assertThat(login.getBody().customerId()).isEqualTo(created.getBody().id());
    }

    @Test
    void unknownEmailIsUnauthorized() {
        ResponseEntity<String> login = rest.postForEntity(
                "/api/auth/login",
                Map.of("email", "yok." + UUID.randomUUID() + "@example.com"),
                String.class);
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
