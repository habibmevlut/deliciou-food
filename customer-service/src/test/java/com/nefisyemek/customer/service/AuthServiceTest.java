package com.nefisyemek.customer.service;

import com.nefisyemek.customer.controller.dto.LoginRequest;
import com.nefisyemek.customer.domain.Customer;
import com.nefisyemek.customer.exception.UnauthorizedException;
import com.nefisyemek.customer.repository.CustomerRepository;
import com.nefisyemek.customer.security.JwtIssuer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private CustomerRepository customers;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(customers, new JwtIssuer("nefis-yemek-dev-jwt-secret-do-not-use-in-prod!", 8));
    }

    @Test
    void loginIssuesTokenForKnownEmail() {
        Customer customer = new Customer("Ali", "ali@example.com", "555");
        ReflectionTestUtils.setField(customer, "id", UUID.randomUUID());
        when(customers.findByEmailIgnoreCase("ali@example.com")).thenReturn(Optional.of(customer));

        var response = authService.login(new LoginRequest("ali@example.com"));

        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.accessToken()).isNotBlank();
        assertThat(response.customerId()).isEqualTo(customer.getId());
    }

    @Test
    void loginUnknownEmailIsUnauthorized() {
        when(customers.findByEmailIgnoreCase("yok@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("yok@example.com")))
                .isInstanceOf(UnauthorizedException.class);
    }
}
