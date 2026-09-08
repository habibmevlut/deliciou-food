package com.nefisyemek.customer.controller.dto;

import com.nefisyemek.customer.domain.Address;

import java.time.Instant;
import java.util.UUID;

public record AddressResponse(
        UUID id,
        UUID customerId,
        String title,
        String street,
        String city,
        Instant createdAt,
        Instant updatedAt
) {
    public static AddressResponse from(Address address) {
        return new AddressResponse(
                address.getId(),
                address.getCustomerId(),
                address.getTitle(),
                address.getStreet(),
                address.getCity(),
                address.getCreatedAt(),
                address.getUpdatedAt()
        );
    }
}
