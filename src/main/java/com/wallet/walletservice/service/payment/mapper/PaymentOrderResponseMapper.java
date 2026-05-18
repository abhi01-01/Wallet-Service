package com.wallet.walletservice.service.payment.mapper;

import com.wallet.walletservice.domain.entity.PaymentOrder;
import com.wallet.walletservice.dto.response.PaymentOrderResponse;
import org.springframework.stereotype.Component;

@Component
public class PaymentOrderResponseMapper {

    public PaymentOrderResponse toResponse(PaymentOrder paymentOrder, String currency) {
        return PaymentOrderResponse.builder()
                .razorpayOrderId(paymentOrder.getRazorpayOrderId())
                .amount(paymentOrder.getAmount())
                .currency(currency)
                .status(paymentOrder.getStatus().name())
                .build();
    }
}
