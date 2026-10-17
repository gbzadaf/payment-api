package com.gabrielf.payment_api.dto;

import com.gabrielf.payment_api.model.Payment;
import com.gabrielf.payment_api.model.enums.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentResponse(
        UUID id,
        BigDecimal amount,
        String currency,
        PaymentStatus status,
        String description,
        Instant createdAt,
        Instant updatedAt

) {
    public static PaymentResponse from(Payment payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getStatus(),
                payment.getDescription(),
                payment.getCreatedAt(),
                payment.getUpdatedAt()
        );
    }
}
