package com.nefisyemek.delivery.service;
import com.nefisyemek.delivery.controller.dto.CreateDeliveryRequest;
import com.nefisyemek.delivery.domain.Delivery;
import com.nefisyemek.delivery.domain.DeliveryStatus;
import com.nefisyemek.delivery.repository.DeliveryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeliveryServiceTest {
    @Mock DeliveryRepository repo;
    @InjectMocks DeliveryService service;
    @Test
    void createDefaultsAssigned() {
        when(repo.save(any(Delivery.class))).thenAnswer(i -> i.getArgument(0));
        var r = service.create(new CreateDeliveryRequest(UUID.randomUUID(), "Ahmet", null));
        assertThat(r.status()).isEqualTo(DeliveryStatus.ASSIGNED);
        assertThat(r.courierName()).isEqualTo("Ahmet");
    }
}
