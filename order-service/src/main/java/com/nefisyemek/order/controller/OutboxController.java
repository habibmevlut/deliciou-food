package com.nefisyemek.order.controller;

import com.nefisyemek.order.controller.dto.OutboxMessageResponse;
import com.nefisyemek.order.repository.OutboxMessageRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Öğrenme endpoint'i: sipariş Kafka'ya gitmeden önce outbox satırı burada durur.
 * Path literal `/outbox` — `{id}` UUID ile karışmasın diye ayrı controller.
 */
@RestController
@RequestMapping("/api/orders/outbox")
public class OutboxController {

    private final OutboxMessageRepository outbox;

    public OutboxController(OutboxMessageRepository outbox) {
        this.outbox = outbox;
    }

    @GetMapping
    public List<OutboxMessageResponse> list(
            @RequestParam(name = "unpublishedOnly", defaultValue = "false") boolean unpublishedOnly
    ) {
        if (unpublishedOnly) {
            return outbox.findTop50ByPublishedAtIsNullOrderByCreatedAtAsc().stream()
                    .map(OutboxMessageResponse::from)
                    .toList();
        }
        return outbox.findTop50ByOrderByCreatedAtDesc().stream()
                .map(OutboxMessageResponse::from)
                .toList();
    }
}
