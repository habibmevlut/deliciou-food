package com.nefisyemek.notification.service;
import com.nefisyemek.notification.controller.dto.CreateNotificationRequest;
import com.nefisyemek.notification.domain.Notification;
import com.nefisyemek.notification.domain.NotificationChannel;
import com.nefisyemek.notification.repository.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {
    @Mock NotificationRepository repo;
    @InjectMocks NotificationService service;
    @Test
    void sendPersistsMock() {
        when(repo.save(any(Notification.class))).thenAnswer(i -> i.getArgument(0));
        var r = service.send(new CreateNotificationRequest("555", NotificationChannel.SMS, "Siparis alindi"));
        assertThat(r.channel()).isEqualTo(NotificationChannel.SMS);
        assertThat(r.message()).isEqualTo("Siparis alindi");
    }
}
