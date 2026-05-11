package com.wallet.walletservice.service;

import jakarta.mail.internet.MimeMessage;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;
    private final String fromAddress;

    public EmailService(
            JavaMailSender mailSender,
            @Value("${spring.mail.username}") @NonNull String fromAddress) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
    }

    @Async      // Prevents SMTP network latency from blocking the main Tomcat thread
    public void sendOtpEmail(@NonNull String toEmail, @NonNull String otp){
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromAddress);
            helper.setTo(toEmail);
            helper.setSubject("Your walletService Verification code");
            helper.setText(buildOtpEmailHtml(otp), true);   // true = HTML

            mailSender.send(message);
            log.info("OTP email sent to {} {}", toEmail, otp);

        } catch (Exception e) {
            log.error("Failed to send OTP email to {}: {}", toEmail, e.getMessage());
        }
    }

    private String buildOtpEmailHtml(String otp){
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
                """.formatted(otp) ;
    }

}
