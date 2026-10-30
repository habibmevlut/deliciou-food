package com.nefisyemek.gateway;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.web.server.SecurityWebFilterChain;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

/**
 * JWT yalnızca kapıda. Backend'ler token doğrulamaz — README'deki "tek yerde Security" budur.
 */
@Configuration
@EnableWebFluxSecurity
public class GatewaySecurityConfig {

    @Bean
    SecurityWebFilterChain securityWebFilterChain(
            ServerHttpSecurity http,
            @Value("${nefis.security.enabled:true}") boolean securityEnabled
    ) {
        http.csrf(ServerHttpSecurity.CsrfSpec::disable);
        if (!securityEnabled) {
            return http.authorizeExchange(exchanges -> exchanges.anyExchange().permitAll()).build();
        }
        return http
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers("/actuator/**", "/fallback/**").permitAll()
                        .pathMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                        .pathMatchers(HttpMethod.POST, "/api/customers").permitAll()
                        .anyExchange().authenticated())
                .oauth2ResourceServer(oauth -> oauth.jwt(Customizer.withDefaults()))
                .build();
    }

    @Bean
    @ConditionalOnProperty(name = "nefis.security.enabled", havingValue = "true", matchIfMissing = true)
    ReactiveJwtDecoder jwtDecoder(@Value("${nefis.jwt.secret}") String secret) {
        SecretKey key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        return NimbusReactiveJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
    }
}
