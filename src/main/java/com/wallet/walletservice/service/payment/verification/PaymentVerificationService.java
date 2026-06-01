package com.wallet.walletservice.service.payment.verification;

import com.wallet.walletservice.domain.entity.PaymentOrder;
import com.wallet.walletservice.domain.enums.PaymentOrderStatus;
import com.wallet.walletservice.exception.PaymentException;
import com.wallet.walletservice.repository.PaymentOrderRepository;
import com.wallet.walletservice.service.payment.gateway.PaymentSignatureVerifier;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentVerificationService {

    private final PaymentOrderRepository paymentOrderRepository;
    private final PaymentSignatureVerifier signatureVerifier;
    private final PaymentUserGuard paymentUserGuard;
    private final WalletCreditService walletCreditService;
    private final MeterRegistry meterRegistry;

    public void verifyPayment(
            String userId,
            String razorpayOrderId,
            String razorpayPaymentId,
            String razorpaySignature
    ) {
        razorpayOrderId = normalize(razorpayOrderId);
        razorpayPaymentId = normalize(razorpayPaymentId);
        razorpaySignature = normalize(razorpaySignature);

        log.info("Initiating payment verification for user={} orderId={} paymentId={}", userId, razorpayOrderId, razorpayPaymentId);

        try {
            PaymentOrder paymentOrder = paymentOrderRepository.findByRazorpayOrderId(razorpayOrderId)
                    .orElseThrow(() -> new PaymentException("Payment order not found", HttpStatus.NOT_FOUND));

            paymentUserGuard.ensureUserCanReceivePaymentCredit(userId);

            if (!paymentOrder.getUserId().equals(userId)) {
                throw new PaymentException("Unauthorized: Order does not belong to user", HttpStatus.FORBIDDEN);
            }

            if (paymentOrder.getStatus() == PaymentOrderStatus.PAID) {
                log.info("Order {} is already marked as PAID", razorpayOrderId);
                recordPaymentAlreadyPaid();
                return;
            }

            String finalRazorpayPaymentId = razorpayPaymentId;
            paymentOrderRepository.findByRazorpayPaymentId(razorpayPaymentId)
                    .ifPresent(existingOrder -> {
                        log.info("Payment ID {} already associated with order {}. Skipping.",
                                finalRazorpayPaymentId,
                                existingOrder.getRazorpayOrderId());
                        throw new PaymentException("Payment ID already processed", HttpStatus.CONFLICT);
                    });

            if (!signatureVerifier.isValid(razorpayOrderId, razorpayPaymentId, razorpaySignature)) {
                log.warn("Invalid payment signature for order: {}", razorpayOrderId);
                paymentOrder.setStatus(PaymentOrderStatus.FAILED);
                paymentOrderRepository.save(paymentOrder);
                throw new PaymentException("Payment verification failed: Invalid signature", HttpStatus.BAD_REQUEST);
            }

            paymentOrder.setRazorpayPaymentId(razorpayPaymentId);
            paymentOrder.setStatus(PaymentOrderStatus.PAID);
            paymentOrderRepository.save(paymentOrder);

            log.info("Payment verified. Crediting user wallet {} with {} {}",
                    userId,
                    paymentOrder.getAmount(),
                    paymentOrder.getAssetCode());
            walletCreditService.creditVerifiedPayment(paymentOrder, razorpayPaymentId);

            recordPaymentSuccess();
            log.info("Payment successfully verified and wallet credited.");
        } catch (Exception e) {
            recordPaymentFailure(e);
            log.error("Payment verification failed for orderId={}: {}", razorpayOrderId, e.getMessage());
            throw e;
        }

    }

    private String normalize(String value) {
        return value == null ? null : value.trim();
    }

    private void recordPaymentSuccess() {
        meterRegistry.counter("business.payment.orders",
                "status", "success",
                "gateway", "razorpay",
                "reason", "signature_passed"
        ).increment();
    }

    private void recordPaymentAlreadyPaid() {
        meterRegistry.counter("business.payment.orders",
                "status", "already_paid",
                "gateway", "razorpay",
                "reason", "signature_processed"
        ).increment();
    }

    private void recordPaymentFailure(Exception e) {
        meterRegistry.counter("business.payment.orders",
                "status", "failed",
                "gateway", "razorpay",
                "reason", e.getClass().getSimpleName()
        ).increment();
    }
}
