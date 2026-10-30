package com.nefisyemek.notification.controller;
import com.nefisyemek.notification.controller.dto.CreateNotificationRequest;
import com.nefisyemek.notification.controller.dto.NotificationResponse;
import com.nefisyemek.notification.service.NotificationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
    private final NotificationService notificationService;
    public NotificationController(NotificationService notificationService) { this.notificationService = notificationService; }

    @PostMapping
    public ResponseEntity<NotificationResponse> send(@Valid @RequestBody CreateNotificationRequest request) {
        NotificationResponse created = notificationService.send(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }
    @GetMapping
    public List<NotificationResponse> findAll() { return notificationService.findAll(); }
    @GetMapping("/{id}")
    public NotificationResponse findById(@PathVariable UUID id) { return notificationService.findById(id); }
}
