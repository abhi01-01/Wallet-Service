package com.wallet.walletservice.service.payment.gateway;

import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import com.razorpay.Utils;
import com.wallet.walletservice.exception.PaymentException;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@Slf4j
public class RazorpayPaymentGateway implements PaymentGateway, PaymentSignatureVerifier {

    private final String razorpayKeyId;
    private final String razorpayKeySecret;

    public RazorpayPaymentGateway(
            @Value("${razorpay.key.id}") String razorpayKeyId,
            @Value("${razorpay.key.secret}") String razorpayKeySecret
    ) {
        this.razorpayKeyId = razorpayKeyId;
        this.razorpayKeySecret = razorpayKeySecret;
    }

    @Override
    public String createOrder(BigDecimal amount, String currency) {
        try {
            JSONObject orderRequest = new JSONObject();
            orderRequest.put("amount", toSmallestCurrencyUnit(amount));
            orderRequest.put("currency", currency);

            Order razorpayOrder = createRazorpayClient().orders.create(orderRequest);
            return razorpayOrder.get("id");
        } catch (RazorpayException e) {
            log.error("Failed to create Razorpay order", e);
            throw new PaymentException("Could not initiate payment", HttpStatus.BAD_GATEWAY, e);
        }
    }

    @Override
    public boolean isValid(String razorpayOrderId, String razorpayPaymentId, String razorpaySignature) {
        try {
            JSONObject options = new JSONObject();
            options.put("razorpay_order_id", razorpayOrderId);
            options.put("razorpay_payment_id", razorpayPaymentId);
            options.put("razorpay_signature", razorpaySignature);
            return Utils.verifyPaymentSignature(options, razorpayKeySecret);
        } catch (RazorpayException e) {
            log.error("Error verifying payment signature for order: {}", razorpayOrderId, e);
            throw new PaymentException("Payment verification error", HttpStatus.BAD_GATEWAY, e);
        }
    }

    private long toSmallestCurrencyUnit(BigDecimal amount) {
        // Razorpay expects INR amounts in paise: 100 INR -> 10000 paise.
        return amount.multiply(new BigDecimal(100)).longValue();
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
