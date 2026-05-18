package com.wallet.walletservice.service.payment.verification;

import com.wallet.walletservice.domain.entity.PaymentOrder;
import com.wallet.walletservice.dto.request.TopUpRequest;
import com.wallet.walletservice.service.wallet.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class WalletCreditService {

    private final WalletService walletService;

    public void creditVerifiedPayment(PaymentOrder paymentOrder, String razorpayPaymentId) {
        TopUpRequest topUpRequest = TopUpRequest.builder()
                .userId(paymentOrder.getUserId())
                .assetCode(paymentOrder.getAssetCode())
                .amount(paymentOrder.getAmount())
                .idempotencyKey("rzp_" + razorpayPaymentId)
                .description("User purchased credits via Razorpay: " + razorpayPaymentId)
                .build();

        walletService.topUp(topUpRequest);
    }
}
