package com.nefisyemek.customer.controller.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record CreateCustomerRequest(
        @NotBlank String fullName,
        @NotBlank @Email String email,
        @NotBlank String phone
) {
}
