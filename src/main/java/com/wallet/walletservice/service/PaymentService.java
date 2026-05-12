package com.wallet.walletservice.service;

import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import com.razorpay.Utils;
import com.wallet.walletservice.domain.entity.PaymentOrder;
import com.wallet.walletservice.domain.enums.PaymentOrderStatus;
import com.wallet.walletservice.dto.request.PaymentOrderRequest;
import com.wallet.walletservice.dto.request.TopUpRequest;
import com.wallet.walletservice.dto.response.PaymentOrderResponse;
import com.wallet.walletservice.exception.PaymentException;
import com.wallet.walletservice.repository.PaymentOrderRepository;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
@Slf4j
public class PaymentService {

    private final String razorpayKeyId;
    private final String razorpayKeySecret;
    private final PaymentOrderRepository paymentOrderRepository;
    private final WalletService walletService;


    public PaymentService(
            @Value("${razorpay.key.id}") String razorpayKeyId,
            @Value("${razorpay.key.secret}") String razorpayKeySecret,
            PaymentOrderRepository paymentOrderRepository,
            WalletService walletService
    ){
        this.razorpayKeyId = razorpayKeyId;
        this.razorpayKeySecret = razorpayKeySecret;
        this.paymentOrderRepository = paymentOrderRepository;
        this.walletService = walletService;
    }

    @Transactional
    public PaymentOrderResponse createOrder(String userId, PaymentOrderRequest req){

        try {
            // 1. Convert to smallest subunit (₹100 -> 10000 paise)
            BigDecimal amountInSmallestUnit = req.getAmount().multiply(new BigDecimal(100));

            JSONObject orderRequest = new JSONObject();
            orderRequest.put("amount", amountInSmallestUnit.longValue());
            orderRequest.put("currency", "INR");

            // 2. Server-to-Server API call
            Order razorpayOrder = createRazorpayClient().orders.create(orderRequest);
            String rzpOrderId = razorpayOrder.get("id");

            // 3. Persist the intent in our database

            PaymentOrder paymentOrder = PaymentOrder.builder()
                    .userId(userId)
                    .razorpayOrderId(rzpOrderId)
                    .amount(req.getAmount())
                    .assetCode(req.getAssetCode())
                    .status(PaymentOrderStatus.CREATED)
                    .build();
            paymentOrderRepository.save(paymentOrder);

            return PaymentOrderResponse.builder()
                    .razorpayOrderId(rzpOrderId)
                    .amount(req.getAmount())
                    .currency("INR")
                    .status(paymentOrder.getStatus().name())
                    .build();

        } catch (RazorpayException e) {
            log.error("Failed to create Razorpay order for user: {}", userId, e);
            throw new PaymentException("Could not initiate payment", HttpStatus.BAD_GATEWAY, e);
        }
    }

    @Transactional
    public void verifyPayment(String userId, String razorpayOrderId, String razorpayPaymentId, String razorpaySignature){

        razorpayOrderId = normalize(razorpayOrderId);
        razorpayPaymentId = normalize(razorpayPaymentId);
        razorpaySignature = normalize(razorpaySignature);

        log.info("Verifying payment for user={} orderId={} paymentId={}", userId, razorpayOrderId, razorpayPaymentId);

        // 1. Find the order in our DB

        PaymentOrder paymentOrder = paymentOrderRepository.findByRazorpayOrderId(razorpayOrderId)
                .orElseThrow(() -> new PaymentException("Payment order not found", HttpStatus.NOT_FOUND));

        if(!paymentOrder.getUserId().equals(userId)){
            throw new PaymentException("Unauthorized: Order does not belong to user", HttpStatus.FORBIDDEN);
        }

        if (paymentOrder.getStatus() == PaymentOrderStatus.PAID) {
            log.info("Order {} is already marked as PAID", razorpayOrderId);
            return; // Idempotent
        }

        // 2. Verify Razorpay Signature

        try {
            JSONObject options = new JSONObject();
            options.put("razorpay_order_id", razorpayOrderId);
            options.put("razorpay_payment_id", razorpayPaymentId);
            options.put("razorpay_signature", razorpaySignature);

            boolean isValid = Utils.verifyPaymentSignature(options, razorpayKeySecret);

            if (!isValid) {
                log.warn("Invalid payment signature for order: {}", razorpayOrderId);
                paymentOrder.setStatus(PaymentOrderStatus.FAILED);
                paymentOrderRepository.save(paymentOrder);
                throw new PaymentException("Payment verification failed: Invalid signature", HttpStatus.BAD_REQUEST);
            }

        }catch (RazorpayException e) {
            log.error("Error verifying payment signature for order: {}", razorpayOrderId, e);
            throw new PaymentException("Payment verification error", HttpStatus.BAD_GATEWAY, e);
        }

        // 3. Mark as PAID
        paymentOrder.setRazorpayPaymentId(razorpayPaymentId);
        paymentOrder.setStatus(PaymentOrderStatus.PAID);
        paymentOrderRepository.save(paymentOrder);

        // 4. ACT AS SYSTEM to top up the user's wallet
        log.info("Payment verified. Crediting user wallet {} with {} {}", userId, paymentOrder.getAmount(), paymentOrder.getAssetCode());

        TopUpRequest topUpRequest = TopUpRequest.builder()
                .userId(userId)
                .assetCode(paymentOrder.getAssetCode())
                .amount(paymentOrder.getAmount())
                .idempotencyKey("rzp_" + razorpayPaymentId)
                .description("User purchased credits via Razorpay: " + razorpayPaymentId)
                .build();

        // Call internal service
        walletService.topUp(topUpRequest);
    }

    private String normalize(String value) {
        return value == null ? null : value.trim();
    }

    private RazorpayClient createRazorpayClient() {
        try {
            return new RazorpayClient(razorpayKeyId, razorpayKeySecret);
        } catch (RazorpayException e) {
            log.error("Failed to initialize Razorpay client", e);
            throw new PaymentException("Payment gateway is unavailable", HttpStatus.SERVICE_UNAVAILABLE, e);
        }
    }

}
