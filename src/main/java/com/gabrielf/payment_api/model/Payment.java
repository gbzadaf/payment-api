package com.gabrielf.payment_api.model;

import com.gabrielf.payment_api.exception.InvalidPaymentStateException;
import com.gabrielf.payment_api.model.enums.PaymentStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    @Column(length = 255)
    private String description;

    @Column(name = "idempotency_key", nullable = false, unique = true,
            updatable = false, length = 100)
    private String idempotencyKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;


    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }


    public Payment(BigDecimal amount, String currency,
                   String description, String idempotencyKey) {
        this.amount = amount;
        this.currency = currency;
        this.description = description;
        this.idempotencyKey = idempotencyKey;
        this.status = PaymentStatus.PENDING;
    }

    //maquina de estados, ao inves de setters, a entidade ganha metodos com regra. regras de dominio, service orquestra.

    public void approve() {
        transitionTo(PaymentStatus.APPROVED, PaymentStatus.PENDING);
    }

    public void fail() {
        transitionTo(PaymentStatus.FAILED, PaymentStatus.PENDING);
    }

    public void refund() {
        transitionTo(PaymentStatus.REFUNDED, PaymentStatus.PENDING);
    }


    private void transitionTo (PaymentStatus target, PaymentStatus requiredCurrent) {
        if (this.status != requiredCurrent) {
            throw new InvalidPaymentStateException(this.status, target);
        }
        this.status = target;
    }

}
