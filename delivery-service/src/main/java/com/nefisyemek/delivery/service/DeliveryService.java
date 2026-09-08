package com.nefisyemek.delivery.service;
import com.nefisyemek.delivery.controller.dto.*;
import com.nefisyemek.delivery.domain.Delivery;
import com.nefisyemek.delivery.domain.DeliveryStatus;
import com.nefisyemek.delivery.exception.ResourceNotFoundException;
import com.nefisyemek.delivery.repository.DeliveryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;

@Service
public class DeliveryService {
    private final DeliveryRepository repo;
    public DeliveryService(DeliveryRepository repo) { this.repo = repo; }

    @Transactional
    public DeliveryResponse create(CreateDeliveryRequest req) {
        DeliveryStatus status = req.status() == null ? DeliveryStatus.ASSIGNED : req.status();
        return DeliveryResponse.from(repo.save(new Delivery(req.orderId(), req.courierName(), status)));
    }
    @Transactional(readOnly = true)
    public List<DeliveryResponse> findAll() { return repo.findAll().stream().map(DeliveryResponse::from).toList(); }
    @Transactional(readOnly = true)
    public DeliveryResponse findById(UUID id) { return DeliveryResponse.from(get(id)); }
    @Transactional
    public DeliveryResponse update(UUID id, UpdateDeliveryRequest req) {
        Delivery d = get(id);
        d.setOrderId(req.orderId());
        d.setCourierName(req.courierName());
        d.setStatus(req.status());
        Delivery saved = repo.save(d);
        repo.flush();
        return DeliveryResponse.from(saved);
    }
    @Transactional
    public void delete(UUID id) { repo.delete(get(id)); }
    private Delivery get(UUID id) {
        return repo.findById(id).orElseThrow(() -> new ResourceNotFoundException("Delivery not found: " + id));
    }
}
