package com.nefisyemek.restaurant.controller.dto;

import com.nefisyemek.restaurant.domain.Restaurant;

import java.time.Instant;
import java.util.UUID;

public record RestaurantResponse(
        UUID id,
        String name,
        String address,
        String cuisine,
        boolean open,
        Instant createdAt,
        Instant updatedAt
) {
    public static RestaurantResponse from(Restaurant restaurant) {
        return new RestaurantResponse(
                restaurant.getId(),
                restaurant.getName(),
                restaurant.getAddress(),
                restaurant.getCuisine(),
                restaurant.isOpen(),
                restaurant.getCreatedAt(),
                restaurant.getUpdatedAt()
        );
    }
}
