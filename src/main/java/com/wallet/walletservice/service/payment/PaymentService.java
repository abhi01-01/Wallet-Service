package com.wallet.walletservice.service.payment;

import com.wallet.walletservice.dto.request.PaymentOrderRequest;
import com.wallet.walletservice.dto.response.PaymentOrderResponse;
import com.wallet.walletservice.service.payment.cleanup.PaymentCleanupService;
import com.wallet.walletservice.service.payment.order.PaymentOrderService;
import com.wallet.walletservice.service.payment.verification.PaymentVerificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentOrderService paymentOrderService;
    private final PaymentVerificationService paymentVerificationService;
    private final PaymentCleanupService paymentCleanupService;

    @Transactional
    public PaymentOrderResponse createOrder(String userId, PaymentOrderRequest request) {
        return paymentOrderService.createOrder(userId, request);
    }

    @Transactional
    public void verifyPayment(
            String userId,
            String razorpayOrderId,
            String razorpayPaymentId,
            String razorpaySignature
    ) {
        paymentVerificationService.verifyPayment(userId, razorpayOrderId, razorpayPaymentId, razorpaySignature);
    }

    @Transactional(readOnly = true)
    public PaymentOrderResponse getOrderStatus(String userId, String razorpayOrderId) {
        return paymentOrderService.getOrderStatus(userId, razorpayOrderId);
    }

    @Transactional
    public void cleanUpStaleOrders() {
        paymentCleanupService.cleanUpStaleOrders();
    }
}
