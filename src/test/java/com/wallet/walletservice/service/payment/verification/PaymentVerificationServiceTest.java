package com.wallet.walletservice.service.payment.verification;

import com.wallet.walletservice.domain.entity.PaymentOrder;
import com.wallet.walletservice.domain.enums.PaymentOrderStatus;
import com.wallet.walletservice.exception.PaymentException;
import com.wallet.walletservice.repository.PaymentOrderRepository;
import com.wallet.walletservice.service.payment.gateway.PaymentSignatureVerifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

    @InjectMocks
    private PaymentVerificationService paymentVerificationService;

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
    }

    @Test
    void verifyPayment_WhenOrderAlreadyPaid_ReturnsWithoutCreditingAgain() {
        PaymentOrder order = order(PaymentOrderStatus.PAID);
        when(paymentOrderRepository.findByRazorpayOrderId("order-1")).thenReturn(Optional.of(order));

        paymentVerificationService.verifyPayment("user-1", "order-1", "payment-1", "sig-1");

        verify(paymentOrderRepository, never()).findByRazorpayPaymentId("payment-1");
        verifyNoInteractions(signatureVerifier, walletCreditService);
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
