package com.wallet.walletservice.service.notification.template;

import org.springframework.stereotype.Component;

@Component
public class OtpEmailTemplateRenderer implements EmailTemplateRenderer<OtpEmailModel> {

    @Override
    public String render(OtpEmailModel model) {
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
                """.formatted(model.getOtp());
    }
}
