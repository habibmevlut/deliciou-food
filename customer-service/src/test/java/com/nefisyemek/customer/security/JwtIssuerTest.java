package com.nefisyemek.customer.security;

import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwtIssuerTest {

    @Test
    void issuePutsCustomerIdInSubject() throws Exception {
        JwtIssuer issuer = new JwtIssuer("nefis-yemek-dev-jwt-secret-do-not-use-in-prod!", 8);
        UUID id = UUID.randomUUID();

        String token = issuer.issue(id, "ali@example.com");

        SignedJWT jwt = SignedJWT.parse(token);
        assertThat(jwt.getJWTClaimsSet().getSubject()).isEqualTo(id.toString());
        assertThat(jwt.getJWTClaimsSet().getStringClaim("email")).isEqualTo("ali@example.com");
    }
}
