package com.gabrielf.payment_api.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record PaymentRequest(

        @NotNull
        @DecimalMin(value = "0.01")
        @Digits(integer = 17, fraction = 2)
        BigDecimal amount,

        @NotBlank
        @Pattern(regexp = "[A-Z]{3}", message = "must be a 3-letter ISO code, e.g. BRL")
        String currency,

        @Size(max = 255)
        String description
) {
}
