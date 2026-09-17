package com.nefisyemek.customer.service;

import com.nefisyemek.customer.controller.dto.LoginRequest;
import com.nefisyemek.customer.controller.dto.LoginResponse;
import com.nefisyemek.customer.domain.Customer;
import com.nefisyemek.customer.exception.UnauthorizedException;
import com.nefisyemek.customer.repository.CustomerRepository;
import com.nefisyemek.customer.security.JwtIssuer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final CustomerRepository customers;
    private final JwtIssuer jwtIssuer;

    public AuthService(CustomerRepository customers, JwtIssuer jwtIssuer) {
        this.customers = customers;
        this.jwtIssuer = jwtIssuer;
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        Customer customer = customers.findByEmailIgnoreCase(request.email())
                .orElseThrow(() -> new UnauthorizedException("Email bulunamadı: " + request.email()));
        String token = jwtIssuer.issue(customer.getId(), customer.getEmail());
        return new LoginResponse(token, "Bearer", customer.getId(), customer.getEmail());
    }
}
