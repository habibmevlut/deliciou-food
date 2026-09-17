package com.nefisyemek.gateway;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import org.springframework.cloud.circuitbreaker.resilience4j.ReactiveResilience4JCircuitBreakerFactory;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JConfigBuilder;
import org.springframework.cloud.client.circuitbreaker.Customizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Spring Cloud CircuitBreaker YAML {@code resilience4j.circuitbreaker.instances}
 * her sürümde factory'ye gitmeyebilir. Sayılar burada da var ki lab'deki
 * "4 hata → sigorta" davranışı gerçekten çalışsın.
 */
@Configuration
public class GatewayCircuitBreakerConfig {

    @Bean
    Customizer<ReactiveResilience4JCircuitBreakerFactory> nefisCircuitBreakers() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowSize(8)
                .minimumNumberOfCalls(4)
                .permittedNumberOfCallsInHalfOpenState(2)
                .failureRateThreshold(50f)
                .waitDurationInOpenState(Duration.ofSeconds(8))
                .build();
        return factory -> factory.configureDefault(id -> new Resilience4JConfigBuilder(id)
                .circuitBreakerConfig(config)
                .build());
    }
}
