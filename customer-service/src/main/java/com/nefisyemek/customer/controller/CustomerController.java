package com.nefisyemek.customer.controller;

import com.nefisyemek.customer.controller.dto.AddressResponse;
import com.nefisyemek.customer.controller.dto.CreateAddressRequest;
import com.nefisyemek.customer.controller.dto.CreateCustomerRequest;
import com.nefisyemek.customer.controller.dto.CustomerResponse;
import com.nefisyemek.customer.controller.dto.UpdateCustomerRequest;
import com.nefisyemek.customer.service.CustomerService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/customers")
public class CustomerController {

    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @PostMapping
    public ResponseEntity<CustomerResponse> create(@Valid @RequestBody CreateCustomerRequest request) {
        CustomerResponse created = customerService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping
    public List<CustomerResponse> findAll() {
        return customerService.findAll();
    }

    @GetMapping("/{id}")
    public CustomerResponse findById(@PathVariable UUID id) {
        return customerService.findById(id);
    }

    @PutMapping("/{id}")
    public CustomerResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateCustomerRequest request) {
        return customerService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        customerService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/addresses")
    public ResponseEntity<AddressResponse> addAddress(
            @PathVariable UUID id,
            @Valid @RequestBody CreateAddressRequest request) {
        AddressResponse created = customerService.addAddress(id, request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{addressId}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping("/{id}/addresses")
    public List<AddressResponse> listAddresses(@PathVariable UUID id) {
        return customerService.listAddresses(id);
    }
}
