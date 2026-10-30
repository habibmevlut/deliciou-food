package com.nefisyemek.payment.controller;

import com.nefisyemek.payment.controller.dto.ProcessedEventResponse;
import com.nefisyemek.payment.repository.ProcessedEventRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Öğrenme endpoint'i: payment-service'in yuttuğu komut id'leri.
 * Path literal `/processed-events` — `{id}` UUID ile karışmasın diye ayrı controller.
 */
@RestController
@RequestMapping("/api/payments/processed-events")
public class ProcessedEventController {

    private final ProcessedEventRepository processed;

    public ProcessedEventController(ProcessedEventRepository processed) {
        this.processed = processed;
    }

    @GetMapping
    public List<ProcessedEventResponse> list() {
        return processed.findTop50ByOrderByProcessedAtDesc().stream()
                .map(ProcessedEventResponse::from)
                .toList();
    }
}
