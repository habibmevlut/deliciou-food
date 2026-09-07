package com.nefisyemek.restaurant.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateRestaurantRequest(
        @NotBlank String name,
        @NotBlank String address,
        @NotBlank String cuisine,
        @NotNull Boolean open
) {
}
