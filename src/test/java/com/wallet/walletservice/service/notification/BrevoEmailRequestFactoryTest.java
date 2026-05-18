package com.wallet.walletservice.service.notification;

import com.wallet.walletservice.config.BrevoProperties;
import com.wallet.walletservice.service.notification.gateway.EmailMessage;
import com.wallet.walletservice.service.notification.gateway.brevo.BrevoEmailRequestFactory;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BrevoEmailRequestFactoryTest {

    @Test
    void buildPayload_UsesConfiguredSenderAndMessageFields() {
        BrevoProperties properties = new BrevoProperties();
        properties.getApi().setSenderEmail("noreply@example.com");
        properties.getApi().setSenderName("Wallet Team");
        BrevoEmailRequestFactory factory = new BrevoEmailRequestFactory(properties);

        EmailMessage message = EmailMessage.builder()
                .toEmail("user@example.com")
                .subject("Subject")
                .htmlContent("<p>Hello</p>")
                .build();

        JSONObject payload = factory.buildPayload(message);

        assertEquals("Wallet Team", payload.getJSONObject("sender").getString("name"));
        assertEquals("noreply@example.com", payload.getJSONObject("sender").getString("email"));
        assertEquals("user@example.com", payload.getJSONArray("to").getJSONObject(0).getString("email"));
        assertEquals("Subject", payload.getString("subject"));
        assertEquals("<p>Hello</p>", payload.getString("htmlContent"));
    }
}
