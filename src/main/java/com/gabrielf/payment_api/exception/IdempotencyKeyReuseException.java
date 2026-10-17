package com.gabrielf.payment_api.exception;


public class IdempotencyKeyReuseException extends RuntimeException {
    public IdempotencyKeyReuseException(String idempotencyKey) {
        super("Idempotency key already used with a different request: " + idempotencyKey);
    }
}
