package com.wallet.walletservice.service;

import com.wallet.walletservice.config.BrevoProperties;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
@Slf4j
@RequiredArgsConstructor
public class EmailService {

    private final BrevoProperties brevoProperties;
    private final OkHttpClient httpClient = new OkHttpClient();

    @Async
    public void sendOtpEmail(@NonNull String toEmail, @NonNull String otp) {
        String apiKey = brevoProperties.getApi().getKey();
        String senderEmail = brevoProperties.getApi().getSenderEmail();
        String senderName = brevoProperties.getApi().getSenderName();

        if (apiKey == null || apiKey.isBlank() || senderEmail == null || senderEmail.isBlank()) {
            log.error("[EmailService] CRITICAL: Brevo configuration is missing. Check your environment variables.");
            return;
        }

        try {
            // Brevo API Payload construction
            JSONObject payload = new JSONObject();
            
            JSONObject sender = new JSONObject();
            sender.put("name", senderName != null ? senderName : "Wallet Service");
            sender.put("email", senderEmail);
            payload.put("sender", sender);

            JSONObject to = new JSONObject();
            to.put("email", toEmail);
            JSONArray toArray = new JSONArray();
            toArray.put(to);
            payload.put("to", toArray);

            payload.put("subject", "Your WalletService Verification code");
            payload.put("htmlContent", buildOtpEmailHtml(otp));

            RequestBody body = RequestBody.create(
                    payload.toString(),
                    MediaType.parse("application/json; charset=utf-8")
            );

            Request request = new Request.Builder()
                    .url("https://api.brevo.com/v3/smtp/email")
                    .addHeader("api-key", apiKey)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Accept", "application/json")
                    .post(body)
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (response.isSuccessful()) {
                    log.info("[EmailService] SUCCESS: OTP email sent via Brevo to {} (Message ID: {})", 
                        toEmail, response.body() != null ? new JSONObject(response.body().string()).optString("messageId") : "N/A");
                } else {
                    String errorBody = response.body() != null ? response.body().string() : "No error body";
                    log.error("[EmailService] API_ERROR: Brevo rejected request. Status: {} | Reason: {}", 
                        response.code(), errorBody);
                }
            }

        } catch (IOException e) {
            log.error("[EmailService] NETWORK_ERROR: Failed to connect to Brevo API. Error: {}", e.getMessage());
        } catch (Exception e) {
            log.error("[EmailService] UNEXPECTED_ERROR: Internal error sending email to {}. Error: {}", toEmail, e.getMessage());
        }
    }

    private String buildOtpEmailHtml(String otp) {
        return """
                <!DOCTYPE html>
                            <html>
                            <body style="font-family: Arial, sans-serif; background: #f4f4f4; padding: 40px;">
                              <div style="max-width: 480px; margin: auto; background: white; border-radius: 12px;
                                          padding: 40px; box-shadow: 0 4px 20px rgba(0,0,0,0.08);">
                                <h2 style="color: #1a1a2e; margin-bottom: 8px;">Verify your email</h2>
                                <p style="color: #555; margin-bottom: 24px;">
                                  Use the code below to complete your Wallet Service sign-up.
                                  This code expires in <strong>10 minutes</strong>.
                                </p>
                                <div style="background: #f0f4ff; border-radius: 8px; padding: 24px; text-align: center;">
                                  <span style="font-size: 40px; font-weight: 700; letter-spacing: 12px; color: #4f46e5;">
                                    %s
                                  </span>
                                </div>
                              </div>
                            </body>
                            </html>
                """.formatted(otp);
    }
}
