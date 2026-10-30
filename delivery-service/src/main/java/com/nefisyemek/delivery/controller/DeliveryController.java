package com.nefisyemek.delivery.controller;
import com.nefisyemek.delivery.controller.dto.*;
import com.nefisyemek.delivery.service.DeliveryService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/deliveries")
public class DeliveryController {
    private final DeliveryService deliveryService;
    public DeliveryController(DeliveryService deliveryService) { this.deliveryService = deliveryService; }

    @PostMapping
    public ResponseEntity<DeliveryResponse> create(@Valid @RequestBody CreateDeliveryRequest request) {
        DeliveryResponse created = deliveryService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }
    @GetMapping
    public List<DeliveryResponse> findAll() { return deliveryService.findAll(); }
    @GetMapping("/{id}")
    public DeliveryResponse findById(@PathVariable UUID id) { return deliveryService.findById(id); }
    @PutMapping("/{id}")
    public DeliveryResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateDeliveryRequest request) {
        return deliveryService.update(id, request);
    }
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        deliveryService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
