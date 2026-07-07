package com.wallet.walletservice.service.payment.order;

import com.wallet.walletservice.domain.entity.PaymentOrder;
import com.wallet.walletservice.domain.enums.OwnerType;
import com.wallet.walletservice.domain.enums.PaymentOrderStatus;
import com.wallet.walletservice.dto.response.PaymentOrderResponse;
import com.wallet.walletservice.exception.PaymentException;
import com.wallet.walletservice.repository.PaymentOrderRepository;
import com.wallet.walletservice.service.payment.gateway.PaymentGateway;
import com.wallet.walletservice.service.payment.mapper.PaymentOrderResponseMapper;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentOrderServiceTest {

    @Mock
    private PaymentGateway paymentGateway;

    @Mock
    private PaymentOrderRepository paymentOrderRepository;

    @Mock
    private PaymentOrderResponseMapper responseMapper;

    @InjectMocks
    private PaymentOrderService paymentOrderService;

    @Test
    void getOrderStatus_WhenRequesterIsSystem_ReturnsAnyUsersOrder() {
        PaymentOrder order = paymentOrder("user-1", "order_123");
        PaymentOrderResponse mapped = response("order_123");
        when(paymentOrderRepository.findByRazorpayOrderId("order_123")).thenReturn(Optional.of(order));
        when(responseMapper.toResponse(order, "INR")).thenReturn(mapped);

        PaymentOrderResponse response = paymentOrderService.getOrderStatus("SYSTEM_TREASURY", "order_123", OwnerType.SYSTEM);

        assertEquals(mapped, response);
        verify(paymentOrderRepository).findByRazorpayOrderId("order_123");
        verify(paymentOrderRepository, never()).findByRazorpayOrderIdAndUserId("order_123", "SYSTEM_TREASURY");
    }

    @Test
    void getOrderStatus_WhenRequesterIsUser_ReturnsOnlyOwnOrder() {
        PaymentOrder order = paymentOrder("user-1", "order_123");
        PaymentOrderResponse mapped = response("order_123");
        when(paymentOrderRepository.findByRazorpayOrderIdAndUserId("order_123", "user-1")).thenReturn(Optional.of(order));
        when(responseMapper.toResponse(order, "INR")).thenReturn(mapped);

        PaymentOrderResponse response = paymentOrderService.getOrderStatus("user-1", "order_123", OwnerType.USER);

        assertEquals(mapped, response);
        verify(paymentOrderRepository).findByRazorpayOrderIdAndUserId("order_123", "user-1");
        verify(paymentOrderRepository, never()).findByRazorpayOrderId("order_123");
    }

    @Test
    void getOrderStatus_WhenRequesterIsUserAndOrderBelongsToAnotherUser_ThrowsNotFound() {
        when(paymentOrderRepository.findByRazorpayOrderIdAndUserId("order_123", "user-2")).thenReturn(Optional.empty());

        PaymentException exception = assertThrows(
                PaymentException.class,
                () -> paymentOrderService.getOrderStatus("user-2", "order_123", OwnerType.USER)
        );

        assertEquals("Order not found or unauthorized", exception.getMessage());
        verify(paymentOrderRepository).findByRazorpayOrderIdAndUserId("order_123", "user-2");
        verify(paymentOrderRepository, never()).findByRazorpayOrderId("order_123");
    }

    private PaymentOrder paymentOrder(String userId, String razorpayOrderId) {
        return PaymentOrder.builder()
                .userId(userId)
                .razorpayOrderId(razorpayOrderId)
                .amount(BigDecimal.TEN)
                .assetCode("GOLD")
                .status(PaymentOrderStatus.CREATED)
                .build();
    }

    private PaymentOrderResponse response(String razorpayOrderId) {
        return PaymentOrderResponse.builder()
                .razorpayOrderId(razorpayOrderId)
                .amount(BigDecimal.TEN)
                .currency("INR")
                .status("CREATED")
                .build();
    }
}
