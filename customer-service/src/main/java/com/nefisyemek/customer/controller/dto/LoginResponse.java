package com.nefisyemek.customer.controller.dto;

import java.util.UUID;

public record LoginResponse(
        String accessToken,
        String tokenType,
        UUID customerId,
        String email
) {
}
