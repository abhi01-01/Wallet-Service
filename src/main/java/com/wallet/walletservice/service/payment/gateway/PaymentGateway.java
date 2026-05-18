package com.wallet.walletservice.service.payment.gateway;

import java.math.BigDecimal;

public interface PaymentGateway {
    String createOrder(BigDecimal amount, String currency);
}
