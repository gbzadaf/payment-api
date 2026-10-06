package com.gabrielf.payment_api.exception;

import com.gabrielf.payment_api.model.enums.PaymentStatus;

public class InvalidPaymentStateException extends RuntimeException {
    public InvalidPaymentStateException(PaymentStatus current, PaymentStatus target) {
        super("Cannot change payment from " + current + " to " + target);
    }
}
