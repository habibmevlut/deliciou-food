package com.nefisyemek.gateway;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Circuit breaker açıkken gateway buraya düşer. Kullanıcı 30 sn timeout yerine hemen 503 görür.
 */
@RestController
public class FallbackController {

    @RequestMapping("/fallback/{service}")
    public Mono<ResponseEntity<Map<String, String>>> fallback(@PathVariable String service) {
        return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "title", "Service unavailable",
                        "service", service,
                        "detail", "Sigorta atmış (circuit breaker). Servis ayakta değil veya peş peşe hata verdi."
                )));
    }
}
