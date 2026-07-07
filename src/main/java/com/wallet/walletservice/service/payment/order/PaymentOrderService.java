package com.wallet.walletservice.service.payment.order;

import com.wallet.walletservice.domain.entity.PaymentOrder;
import com.wallet.walletservice.domain.enums.OwnerType;
import com.wallet.walletservice.domain.enums.PaymentOrderStatus;
import com.wallet.walletservice.dto.request.PaymentOrderRequest;
import com.wallet.walletservice.dto.response.PaymentOrderResponse;
import com.wallet.walletservice.exception.PaymentException;
import com.wallet.walletservice.repository.PaymentOrderRepository;
import com.wallet.walletservice.service.payment.PaymentConstants;
import com.wallet.walletservice.service.payment.gateway.PaymentGateway;
import com.wallet.walletservice.service.payment.mapper.PaymentOrderResponseMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentOrderService {

    private final PaymentGateway paymentGateway;
    private final PaymentOrderRepository paymentOrderRepository;
    private final PaymentOrderResponseMapper responseMapper;

    public PaymentOrderResponse createOrder(String userId, PaymentOrderRequest request) {
        String razorpayOrderId = paymentGateway.createOrder(request.getAmount(), PaymentConstants.DEFAULT_CURRENCY);

        PaymentOrder paymentOrder = PaymentOrder.builder()
                .userId(userId)
                .razorpayOrderId(razorpayOrderId)
                .amount(request.getAmount())
                .assetCode(request.getAssetCode())
                .status(PaymentOrderStatus.CREATED)
                .build();
        paymentOrderRepository.save(paymentOrder);

        log.info("Order created for user={} with razorpayOrderId={}", userId, paymentOrder.getRazorpayOrderId());
        return responseMapper.toResponse(paymentOrder, PaymentConstants.DEFAULT_CURRENCY);
    }

    public PaymentOrderResponse getOrderStatus(String userId, String razorpayOrderId, OwnerType requesterOwnerType) {
        PaymentOrder order = switch (requesterOwnerType) {
            case SYSTEM -> paymentOrderRepository.findByRazorpayOrderId(razorpayOrderId)
                    .orElseThrow(() -> new PaymentException("Order not found", HttpStatus.NOT_FOUND));
            case USER -> paymentOrderRepository.findByRazorpayOrderIdAndUserId(razorpayOrderId, userId)
                    .orElseThrow(() -> new PaymentException("Order not found or unauthorized", HttpStatus.NOT_FOUND));
        };

        return responseMapper.toResponse(order, PaymentConstants.DEFAULT_CURRENCY);
    }
}
