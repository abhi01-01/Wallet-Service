package com.wallet.walletservice.service.payment.gateway;

public interface PaymentSignatureVerifier {
    boolean isValid(String razorpayOrderId, String razorpayPaymentId, String razorpaySignature);
}
