package com.wallet.walletservice.service.notification;

import com.wallet.walletservice.service.notification.gateway.EmailGateway;
import com.wallet.walletservice.service.notification.gateway.EmailMessage;
import com.wallet.walletservice.service.notification.template.OtpEmailTemplateRenderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EmailNotificationServiceTest {

    @Mock
    private EmailGateway emailGateway;

    @Spy
    private OtpEmailTemplateRenderer otpEmailTemplateRenderer;

    @InjectMocks
    private EmailNotificationService emailNotificationService;

    @Test
    void sendOtpEmail_BuildsOtpMessageAndDelegatesToGateway() {
        emailNotificationService.sendOtpEmail("user@example.com", "123456");

        ArgumentCaptor<EmailMessage> captor = ArgumentCaptor.forClass(EmailMessage.class);
        verify(emailGateway).send(captor.capture());

        EmailMessage message = captor.getValue();
        assertEquals("user@example.com", message.getToEmail());
        assertEquals("Your WalletService Verification code", message.getSubject());
        assertTrue(message.getHtmlContent().contains("123456"));
    }
}
