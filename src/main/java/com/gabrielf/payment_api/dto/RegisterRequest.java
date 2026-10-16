package com.gabrielf.payment_api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank
        @Email
        @Size(max = 150)
        String email,

        @NotBlank
        @Size(min = 8, max = 72)
        String password
) {
    public RegisterRequest {
        email = email == null ? null : email.trim().toLowerCase();
    }
}
