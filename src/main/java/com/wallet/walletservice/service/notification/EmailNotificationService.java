package com.wallet.walletservice.service.notification;

import com.wallet.walletservice.service.notification.gateway.EmailGateway;
import com.wallet.walletservice.service.notification.gateway.EmailMessage;
import com.wallet.walletservice.service.notification.template.OtpEmailModel;
import com.wallet.walletservice.service.notification.template.OtpEmailTemplateRenderer;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmailNotificationService {

    private static final String OTP_SUBJECT = "Your WalletService Verification code";

    private final EmailGateway emailGateway;
    private final OtpEmailTemplateRenderer otpEmailTemplateRenderer;

    @Async
    public void sendOtpEmail(@NonNull String toEmail, @NonNull String otp) {
        EmailMessage message = EmailMessage.builder()
                .toEmail(toEmail)
                .subject(OTP_SUBJECT)
                .htmlContent(otpEmailTemplateRenderer.render(new OtpEmailModel(otp)))
                .build();

        emailGateway.send(message);
    }
}
