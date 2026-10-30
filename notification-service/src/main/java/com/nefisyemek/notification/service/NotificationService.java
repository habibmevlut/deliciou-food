package com.nefisyemek.notification.service;
import com.nefisyemek.notification.controller.dto.CreateNotificationRequest;
import com.nefisyemek.notification.controller.dto.NotificationResponse;
import com.nefisyemek.notification.domain.Notification;
import com.nefisyemek.notification.exception.ResourceNotFoundException;
import com.nefisyemek.notification.repository.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;

@Service
public class NotificationService {
    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private final NotificationRepository repo;
    public NotificationService(NotificationRepository repo) { this.repo = repo; }

    @Transactional
    public NotificationResponse send(CreateNotificationRequest request) {
        // Mock: gercek SMS/push yok. Milestone 2'de Kafka event'i burayi tetikleyecek.
        log.info("[MOCK {}] to={}: {}", request.channel(), request.recipient(), request.message());
        Notification saved = repo.save(new Notification(request.recipient(), request.channel(), request.message()));
        return NotificationResponse.from(saved);
    }
    @Transactional(readOnly = true)
    public List<NotificationResponse> findAll() { return repo.findAll().stream().map(NotificationResponse::from).toList(); }
    @Transactional(readOnly = true)
    public NotificationResponse findById(UUID id) {
        return NotificationResponse.from(repo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found: " + id)));
    }
}
