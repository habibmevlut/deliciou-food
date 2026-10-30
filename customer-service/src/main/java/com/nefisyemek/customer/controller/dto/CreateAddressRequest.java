package com.nefisyemek.customer.controller.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateAddressRequest(
        @NotBlank String title,
        @NotBlank String street,
        @NotBlank String city
) {
}
