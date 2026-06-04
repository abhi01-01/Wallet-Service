package com.wallet.walletservice.service.webhook;

import com.fasterxml.jackson.databind.JsonNode;
import com.wallet.walletservice.domain.entity.PaymentOrder;
import com.wallet.walletservice.domain.entity.WebhookEvent;
import com.wallet.walletservice.domain.enums.PaymentOrderStatus;
import com.wallet.walletservice.dto.request.TopUpRequest;
import com.wallet.walletservice.exception.PaymentException;
import com.wallet.walletservice.repository.PaymentOrderRepository;
import com.wallet.walletservice.service.payment.verification.PaymentUserGuard;
import com.wallet.walletservice.service.wallet.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentCapturedStrategy implements WebhookHandlerStrategy{

    private final PaymentOrderRepository paymentOrderRepository;
    private final WalletService walletService;
    private final PaymentUserGuard paymentUserGuard; // 1. Injected the Guard


    @Override
    public boolean supports(String eventType) {
        return "payment.captured".equals(eventType);
    }

    @Override
    public void process(WebhookEvent event) {
        JsonNode entityNode = event.getPayload().path("payload").path("payment").path("entity");

        String razorpayOrderId = entityNode.path("order_id").asText();
        String razorpayPaymentId = entityNode.path("id").asText();

        PaymentOrder paymentOrder = paymentOrderRepository.findByRazorpayOrderId(razorpayOrderId)
                .orElseThrow(() -> new PaymentException("Payment order not found: " + razorpayOrderId, HttpStatus.NOT_FOUND));

        // 1. Race Condition Idempotency
        // If the user's frontend client already called /verify, the order is PAID. We skip.
        if (paymentOrder.getStatus() == PaymentOrderStatus.PAID) {
            log.info("Webhook reconciliation: Order {} is already PAID. Skipping.", razorpayOrderId);
            return;
        }

        // fix. User Guard Validation (CRITICAL FIX)
        // Ensures closed or non-existent accounts do not receive post-closure webhook credits.
        // If this throws an AuthException, the execution halts, preventing the state transition and credit.
        paymentUserGuard.ensureUserCanReceivePaymentCredit(paymentOrder.getUserId());

        // 2. State Transition
        paymentOrder.setRazorpayPaymentId(razorpayPaymentId);
        paymentOrder.setStatus(PaymentOrderStatus.PAID);
        paymentOrderRepository.save(paymentOrder);

        // 3. Execute Ledger Credit

        TopUpRequest topUpRequest = TopUpRequest.builder()
                .userId(paymentOrder.getUserId())
                .assetCode(paymentOrder.getAssetCode())
                .amount(paymentOrder.getAmount())
                // Use a deterministic idempotency key for the wallet subsystem
                .idempotencyKey("rzp_webhook_" + razorpayPaymentId)
                .description("Automated Webhook Credit: " + razorpayPaymentId)
                .build();

        walletService.topUp(topUpRequest);

        log.info("Webhook reconciliation successful: Wallet credited for order {}", razorpayOrderId);
    }
}
