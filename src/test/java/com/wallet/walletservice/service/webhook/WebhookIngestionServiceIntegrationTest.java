package com.wallet.walletservice.service.webhook;

import com.wallet.walletservice.domain.enums.WebhookStatus;
import com.wallet.walletservice.repository.WebhookEventRepository;
import com.razorpay.RazorpayException;
import com.razorpay.Utils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:webhookingestiontest;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false"
})
@ActiveProfiles("test")
class WebhookIngestionServiceIntegrationTest {

    @Autowired
    private WebhookIngestionService webhookIngestionService;
    @Autowired
    private WebhookEventRepository webhookEventRepository;
    @Value("${razorpay.webhook.secret}")
    private String webhookSecret;

    @Test
    void ingestRazorpayWebhook_WhenSignatureIsValid_PersistsInboxEventAndIgnoresDuplicate() throws RazorpayException {
        String payload = """
                {"event":"payment.captured","payload":{"payment":{"entity":{"id":"pay_ingest_1","order_id":"order_ingest_1"}}}}
                """.trim();
        String signature = Utils.getHash(payload, webhookSecret);

        webhookIngestionService.ingestRazorpayWebhook(payload, signature);
        webhookIngestionService.ingestRazorpayWebhook(payload, signature);

        assertEquals(1, webhookEventRepository.count());
        var event = webhookEventRepository.findByEventId("pay_ingest_1").orElseThrow();
        assertEquals("payment.captured", event.getEventType());
        assertEquals("order_ingest_1", event.getOrderId());
        assertEquals(WebhookStatus.RECEIVED, event.getStatus());
        assertEquals("pay_ingest_1", event.getPayload().path("payload").path("payment").path("entity").path("id").asText());
    }
}
