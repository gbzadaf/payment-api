package com.gabrielf.payment_api.gateway;

import com.gabrielf.payment_api.model.Payment;

public interface PaymentGateway {

    GatewayResult charge(Payment  payment);
}
