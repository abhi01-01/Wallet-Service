package com.wallet.walletservice.controller;

import com.wallet.walletservice.service.webhook.WebhookIngestionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/webhooks")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Webhooks", description = "Endpoints for handling external service notifications (e.g., Razorpay)")
public class WebhookController {

    private final WebhookIngestionService ingestionService;

    @PostMapping("/razorpay")
    @Operation(summary = "Razorpay Webhook Ingestion", description = "Ingests raw webhook events from Razorpay, performs signature verification, and stores them in the transactional inbox.")
    public ResponseEntity<Void> handleRazorpayWebhook(
            @RequestBody String rawPayload,
            @RequestHeader("X-Razorpay-Signature") String signature
    ) {
        log.info("Received Razorpay webhook");
        ingestionService.ingestRazorpayWebhook(rawPayload, signature);
        return ResponseEntity.ok().build();
    }
}
