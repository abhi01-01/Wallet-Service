package com.wallet.walletservice.service.notification.gateway.brevo;

import com.wallet.walletservice.config.BrevoProperties;
import com.wallet.walletservice.service.notification.gateway.EmailGateway;
import com.wallet.walletservice.service.notification.gateway.EmailMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.json.JSONObject;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor
@Slf4j
public class BrevoEmailGateway implements EmailGateway {

    private static final String BREVO_SEND_EMAIL_URL = "https://api.brevo.com/v3/smtp/email";
    private static final MediaType JSON_MEDIA_TYPE = MediaType.parse("application/json; charset=utf-8");

    private final BrevoProperties brevoProperties;
    private final OkHttpClient httpClient;
    private final BrevoEmailRequestFactory requestFactory;

    @Override
    public void send(EmailMessage message) {
        String apiKey = brevoProperties.getApi().getKey();
        String senderEmail = brevoProperties.getApi().getSenderEmail();

        if (apiKey == null || apiKey.isBlank() || senderEmail == null || senderEmail.isBlank()) {
            log.error("[EmailGateway] CRITICAL: Brevo configuration is missing. Check your environment variables.");
            return;
        }

        try {
            Request request = buildRequest(apiKey, requestFactory.buildPayload(message));
            try (Response response = httpClient.newCall(request).execute()) {
                if (response.isSuccessful()) {
                    String messageId = response.body() != null
                            ? new JSONObject(response.body().string()).optString("messageId")
                            : "N/A";
                    log.info("[EmailGateway] ACCEPTED: Brevo accepted email for {} (Message ID: {})",
                            message.getToEmail(),
                            messageId);
                } else {
                    String errorBody = response.body() != null ? response.body().string() : "No error body";
                    log.error("[EmailGateway] API_ERROR: Brevo rejected request. Status: {} | Reason: {}",
                            response.code(),
                            errorBody);
                }
            }
        } catch (IOException e) {
            log.error("[EmailGateway] NETWORK_ERROR: Failed to connect to Brevo API. Error: {}", e.getMessage());
        } catch (Exception e) {
            log.error("[EmailGateway] UNEXPECTED_ERROR: Internal error sending email to {}. Error: {}",
                    message.getToEmail(),
                    e.getMessage());
        }
    }

    private Request buildRequest(String apiKey, JSONObject payload) {
        RequestBody body = RequestBody.create(payload.toString(), JSON_MEDIA_TYPE);

        return new Request.Builder()
                .url(BREVO_SEND_EMAIL_URL)
                .addHeader("api-key", apiKey)
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "application/json")
                .post(body)
                .build();
    }
}
