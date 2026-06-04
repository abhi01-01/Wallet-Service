package com.wallet.walletservice.service.payment.verification;

import com.wallet.walletservice.domain.entity.PaymentOrder;
import com.wallet.walletservice.domain.enums.PaymentOrderStatus;
import com.wallet.walletservice.exception.PaymentException;
import com.wallet.walletservice.repository.PaymentOrderRepository;
import com.wallet.walletservice.service.payment.gateway.PaymentSignatureVerifier;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentVerificationServiceTest {

    @Mock
    private PaymentOrderRepository paymentOrderRepository;
    @Mock
    private PaymentSignatureVerifier signatureVerifier;
    @Mock
    private PaymentUserGuard paymentUserGuard;
    @Mock
    private WalletCreditService walletCreditService;

    private PaymentVerificationService paymentVerificationService;
    private SimpleMeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        paymentVerificationService = new PaymentVerificationService(
                paymentOrderRepository,
                signatureVerifier,
                paymentUserGuard,
                walletCreditService,
                meterRegistry
        );
    }

    @Test
    void verifyPayment_WhenSignatureIsValid_MarksPaidAndCreditsWallet() {
        PaymentOrder order = order(PaymentOrderStatus.CREATED);
        when(paymentOrderRepository.findByRazorpayOrderId("order-1")).thenReturn(Optional.of(order));
        when(paymentOrderRepository.findByRazorpayPaymentId("payment-1")).thenReturn(Optional.empty());
        when(signatureVerifier.isValid("order-1", "payment-1", "sig-1")).thenReturn(true);

        paymentVerificationService.verifyPayment("user-1", " order-1 ", " payment-1 ", " sig-1 ");

        assertEquals(PaymentOrderStatus.PAID, order.getStatus());
        assertEquals("payment-1", order.getRazorpayPaymentId());
        verify(paymentOrderRepository).save(order);
        verify(walletCreditService).creditVerifiedPayment(order, "payment-1");
        assertEquals(1.0, meterRegistry.counter("business.payment.orders",
                "status", "success",
                "gateway", "razorpay",
                "reason", "signature_passed"
        ).count());
    }

    @Test
    void verifyPayment_WhenSignatureIsInvalid_MarksFailedAndDoesNotCreditWallet() {
        PaymentOrder order = order(PaymentOrderStatus.CREATED);
        when(paymentOrderRepository.findByRazorpayOrderId("order-1")).thenReturn(Optional.of(order));
        when(paymentOrderRepository.findByRazorpayPaymentId("payment-1")).thenReturn(Optional.empty());
        when(signatureVerifier.isValid("order-1", "payment-1", "sig-1")).thenReturn(false);

        assertThrows(
                PaymentException.class,
                () -> paymentVerificationService.verifyPayment("user-1", "order-1", "payment-1", "sig-1"));

        assertEquals(PaymentOrderStatus.FAILED, order.getStatus());
        verify(paymentOrderRepository).save(order);
        verifyNoInteractions(walletCreditService);
        assertEquals(1.0, meterRegistry.counter("business.payment.orders",
                "status", "failed",
                "gateway", "razorpay",
                "reason", PaymentException.class.getSimpleName()
        ).count());
    }

    @Test
    void verifyPayment_WhenOrderAlreadyPaid_ReturnsWithoutCreditingAgain() {
        PaymentOrder order = order(PaymentOrderStatus.PAID);
        when(paymentOrderRepository.findByRazorpayOrderId("order-1")).thenReturn(Optional.of(order));

        paymentVerificationService.verifyPayment("user-1", "order-1", "payment-1", "sig-1");

        verify(paymentOrderRepository, never()).findByRazorpayPaymentId("payment-1");
        verifyNoInteractions(signatureVerifier, walletCreditService);
        assertEquals(1.0, meterRegistry.counter("business.payment.orders",
                "status", "already_paid",
                "gateway", "razorpay",
                "reason", "signature_processed"
        ).count());
        assertEquals(0.0, meterRegistry.find("business.payment.orders")
                .tag("status", "failed")
                .counters()
                .stream()
                .mapToDouble(Counter::count)
                .sum());
    }

    @Test
    void verifyPayment_WhenPaymentIdAlreadyProcessed_ThrowsConflictException(){
        // Setup: The current order is CREATED, but the razorpayPaymentId is already tied to another record
        PaymentOrder order = order(PaymentOrderStatus.CREATED);
        PaymentOrder existingOrder = order(PaymentOrderStatus.PAID);
        existingOrder.setRazorpayOrderId("some-other-order");

        when(paymentOrderRepository.findByRazorpayOrderId("order-1")).thenReturn(Optional.of(order));

        // Mock that the payment ID already exists in the database
        when(paymentOrderRepository.findByRazorpayPaymentId("payment-1")).thenReturn(Optional.of(existingOrder));

        PaymentException exception = assertThrows(
                PaymentException.class,
                () -> paymentVerificationService.verifyPayment("user-1", "order-1", "payment-1", "sig-1")
        );

        // Assertions
        assertEquals("Payment ID already processed", exception.getMessage());
        verify(signatureVerifier, never()).isValid(any(), any(), any()); // Signature check is bypassed
        verifyNoInteractions(walletCreditService); // Wallet is never credited

        assertEquals(1.0, meterRegistry.counter("business.payment.orders",
                "status", "failed",
                "gateway", "razorpay",
                "reason", PaymentException.class.getSimpleName()
        ).count());
    }

    @Test
    void verifyPayment_WhenOrderBelongsToDifferentUser_ThrowsForbiddenException(){
        // Setup: Order belongs to "user-1"
        PaymentOrder order = order(PaymentOrderStatus.CREATED);
        when(paymentOrderRepository.findByRazorpayOrderId("order-1")).thenReturn(Optional.of(order));

        // Execution: "malicious-user" tries to verify "user-1" 's order
        PaymentException exception = assertThrows(
                PaymentException.class,
                () -> paymentVerificationService.verifyPayment("malicious-user", "order-1", "payment-1", "sig-1")
        );

        // Assertions
        assertEquals("Unauthorized: Order does not belong to user", exception.getMessage());
        verify(paymentOrderRepository, never()).save(any());
        verifyNoInteractions(walletCreditService);
    }

    @Test
    void verifyPayment_WhenUserGuardRejectsUser_ThrowsExceptionAndHalts(){
        // Setup
        PaymentOrder order = order(PaymentOrderStatus.CREATED);
        when(paymentOrderRepository.findByRazorpayOrderId("order-1")).thenReturn(Optional.of(order));

        // Mock the guard throwing an exception (e.g., account is closed)
        org.mockito.Mockito.doThrow(new com.wallet.walletservice.exception.AuthException("Account is closed."))
                .when(paymentUserGuard).ensureUserCanReceivePaymentCredit("user-1");

        // Execution
        assertThrows(
                com.wallet.walletservice.exception.AuthException.class,
                () -> paymentVerificationService.verifyPayment("user-1", "order-1", "payment-1", "sig-1")
        );

        // Assertions
        verify(paymentOrderRepository, never()).save(any());
        verify(signatureVerifier, never()).isValid(any(), any(), any());
        verifyNoInteractions(walletCreditService);
    }

    private PaymentOrder order(PaymentOrderStatus status) {
        return PaymentOrder.builder()
                .userId("user-1")
                .razorpayOrderId("order-1")
                .amount(new BigDecimal("100.0000"))
                .assetCode("GOLD")
                .status(status)
                .build();
    }
}
