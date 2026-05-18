package com.wallet.walletservice.service.notification.gateway.brevo;

import com.wallet.walletservice.config.BrevoProperties;
import com.wallet.walletservice.service.notification.gateway.EmailMessage;
import lombok.RequiredArgsConstructor;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BrevoEmailRequestFactory {

    private final BrevoProperties brevoProperties;

    public JSONObject buildPayload(EmailMessage message) {
        JSONObject payload = new JSONObject();

        JSONObject sender = new JSONObject();
        sender.put("name", senderName());
        sender.put("email", brevoProperties.getApi().getSenderEmail());
        payload.put("sender", sender);

        JSONObject to = new JSONObject();
        to.put("email", message.getToEmail());
        JSONArray toArray = new JSONArray();
        toArray.put(to);
        payload.put("to", toArray);

        payload.put("subject", message.getSubject());
        payload.put("htmlContent", message.getHtmlContent());
        return payload;
    }

    private String senderName() {
        String configuredSenderName = brevoProperties.getApi().getSenderName();
        return configuredSenderName != null ? configuredSenderName : "Wallet Service";
    }
}
