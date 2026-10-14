package com.gabrielf.payment_api.dto;

import com.gabrielf.payment_api.model.Payment;
import com.gabrielf.payment_api.model.enums.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record WebhookEvent(
        UUID paymentId,
        PaymentStatus status,
        BigDecimal amount,
        String currency,
        Instant occuredAt

) {
    public static WebhookEvent from(Payment payment) {
        return new WebhookEvent(
                payment.getId(),
                payment.getStatus(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getUpdatedAt()
        );
    }
}
