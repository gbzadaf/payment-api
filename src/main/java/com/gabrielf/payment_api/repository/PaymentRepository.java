package com.gabrielf.payment_api.repository;

import com.gabrielf.payment_api.model.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {


    Optional<Payment> findByIdempotencyKey(String idempotencyKey);

}
