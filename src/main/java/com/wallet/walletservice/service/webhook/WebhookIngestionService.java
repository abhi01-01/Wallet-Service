package com.wallet.walletservice.service.webhook;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.razorpay.RazorpayException;
import com.razorpay.Utils;
import com.wallet.walletservice.domain.entity.WebhookEvent;
import com.wallet.walletservice.exception.PaymentException;
import com.wallet.walletservice.exception.SecurityException;
import com.wallet.walletservice.repository.WebhookEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class WebhookIngestionService {

    private final WebhookEventRepository eventRepository;
    private final ObjectMapper objectMapper;

    @Value("${razorpay.webhook.secret}")
    private String webhookSecret;

    @Transactional
    public void ingestRazorpayWebhook(String rawPayload, String signature) {
        // 1. Verify Signature using official Razorpay SDK
        try {
            if (!Utils.verifyWebhookSignature(rawPayload, signature, webhookSecret)) {
                log.warn("Invalid webhook signature received");
                throw new SecurityException("Invalid webhook signature provided by external gateway");
            }
        } catch (RazorpayException e) {
            log.error("Signature verification failed", e);
            throw new SecurityException("Webhook signature verification failed due to internal error");
        }

        try {
            // 2. Parse Routing Metadata
            JsonNode payloadNode = objectMapper.readTree(rawPayload);
            
            // Razorpay top-level fields
            String eventId = payloadNode.path("payload").path("payment").path("entity").path("id").asText();
            String eventType = payloadNode.path("event").asText();
            
            // Extract Order ID to link with PaymentOrder
            String orderId = extractOrderId(payloadNode);

            // 3. Save to Transactional Inbox (Idempotent by DB Unique Constraint)
            if (eventRepository.findByEventId(eventId).isPresent()) {
                log.info("Duplicate webhook event received: {} Skipping ingestion.", eventId);
                return;
            }

            WebhookEvent event = WebhookEvent.builder()
                    .eventId(eventId)
                    .eventType(eventType)
                    .orderId(orderId)
                    .payload(payloadNode)
                    .build();

            eventRepository.save(event);
            log.info("Webhook event ingested successfully: {} ({})", eventId, eventType);

        } catch (Exception e) {
            log.error("Failed to parse or ingest webhook payload", e);
            throw new PaymentException("Webhook ingestion failed: " + e.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }

    private String extractOrderId(JsonNode payloadNode) {
        // Razorpay structure: payload -> payment -> entity -> order_id
        JsonNode paymentNode = payloadNode.path("payload").path("payment");
        if (!paymentNode.isMissingNode()) {
            return paymentNode.path("entity").path("order_id").asText();
        }
        
        // Alternative: payload -> order -> entity -> id
        JsonNode orderNode = payloadNode.path("payload").path("order");
        if (!orderNode.isMissingNode()) {
            return orderNode.path("entity").path("id").asText();
        }
        
        return null;
    }
}
