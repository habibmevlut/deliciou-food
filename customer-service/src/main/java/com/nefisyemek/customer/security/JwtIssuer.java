package com.nefisyemek.customer.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * Kapı (gateway) aynı secret ile token'ı doğrular. Şifre yok — laboratuvar:
 * e-posta ile müşteriyi tanır, imzalı bilet (JWT) verir.
 */
@Component
public class JwtIssuer {

    private final byte[] secret;
    private final long ttlHours;

    public JwtIssuer(
            @Value("${nefis.jwt.secret}") String secret,
            @Value("${nefis.jwt.ttl-hours:8}") long ttlHours
    ) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.ttlHours = ttlHours;
    }

    public String issue(UUID customerId, String email) {
        Instant now = Instant.now();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(customerId.toString())
                .claim("email", email)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(ttlHours * 3600)))
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        try {
            jwt.sign(new MACSigner(secret));
        } catch (JOSEException e) {
            throw new IllegalStateException("JWT imzalanamadı", e);
        }
        return jwt.serialize();
    }
}
