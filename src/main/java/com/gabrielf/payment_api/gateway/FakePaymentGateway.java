package com.gabrielf.payment_api.gateway;

import com.gabrielf.payment_api.model.Payment;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class FakePaymentGateway implements PaymentGateway {

    private static final BigDecimal DECLINE_ABOVE = new BigDecimal("5000.00");


    @Override
    public GatewayResult charge(Payment payment) {
        return payment.getAmount().compareTo(DECLINE_ABOVE) > 0
                ? GatewayResult.DECLINED
                : GatewayResult.APPROVED;
    }
}
