package com.wallet.walletservice.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request to verify a payment")
public class PaymentVerifyRequest {

    @NotBlank(message = "Razorpay payment ID is required")
    @Schema(description = "Payment ID returned by Razorpay", example = "pay_NHD782hdjks")
    private String razorpayPaymentId;

    @NotBlank(message = "Razorpay Order ID is required")
    @Schema(description = "Order ID returned by Razorpay", example = "order_983hdks82")
    private String razorpayOrderId;

    @NotBlank(message = "Razorpay Signature is required")
    @Schema(description = "HMAC signature for verification", example = "signature_293hdjsk...")
    private String razorpaySignature;
}
