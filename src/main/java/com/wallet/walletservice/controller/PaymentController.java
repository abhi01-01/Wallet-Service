package com.wallet.walletservice.controller;

import com.wallet.walletservice.dto.request.PaymentOrderRequest;
import com.wallet.walletservice.dto.request.PaymentVerifyRequest;
import com.wallet.walletservice.dto.response.ApiResponse;
import com.wallet.walletservice.dto.response.PaymentOrderResponse;
import com.wallet.walletservice.domain.enums.OwnerType;
import com.wallet.walletservice.service.payment.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@Tag(name = "Payments", description = "Endpoints for Razorpay payment integration and wallet top-ups")
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/create-order")
    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "Create Razorpay order", description = "Initialize a payment process by creating an order in Razorpay.")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Order created successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid payment request")
    })
    public ResponseEntity<com.wallet.walletservice.dto.response.ApiResponse<PaymentOrderResponse>> createOrder(
            @AuthenticationPrincipal String userId,
            @Valid @RequestBody PaymentOrderRequest request
            ){
        PaymentOrderResponse response = paymentService.createOrder(userId, request);
        return ResponseEntity.ok(com.wallet.walletservice.dto.response.ApiResponse.ok("Order created successfully", response));
    }

    @PostMapping("/verify")
    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "Verify payment", description = "Verify the Razorpay payment signature and credit the user's wallet upon success.")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Payment verified and wallet credited"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Payment verification failed or invalid signature")
    })
    public ResponseEntity<com.wallet.walletservice.dto.response.ApiResponse<String>> verifyPayment(
            @AuthenticationPrincipal String userId,
            @Valid @RequestBody PaymentVerifyRequest request
            ){
        paymentService.verifyPayment(
                userId,
                request.getRazorpayOrderId(),
                request.getRazorpayPaymentId(),
                request.getRazorpaySignature()
        );

        return ResponseEntity.ok(com.wallet.walletservice.dto.response.ApiResponse.ok("Payment verified and wallet credited successfully", null));
    }


    @GetMapping("/order-status/{orderId}")
    @PreAuthorize("hasAnyRole('USER','SYSTEM')")
    @Operation(summary = "Get Payment Order Status", description = "Poll this endpoint to verify if the payment was successful.")
    public ResponseEntity<com.wallet.walletservice.dto.response.ApiResponse<PaymentOrderResponse>> getOrderStatus(
            @AuthenticationPrincipal String userId,
            @PathVariable String orderId,
            Authentication authentication
    ){
        PaymentOrderResponse response = paymentService.getOrderStatus(userId, orderId, requesterOwnerType(authentication));
        return ResponseEntity.ok(com.wallet.walletservice.dto.response.ApiResponse.ok("Order status retrieved", response));
    }

    private OwnerType requesterOwnerType(Authentication authentication) {
        boolean system = authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_SYSTEM".equals(authority.getAuthority()));
        return system ? OwnerType.SYSTEM : OwnerType.USER;
    }

}
