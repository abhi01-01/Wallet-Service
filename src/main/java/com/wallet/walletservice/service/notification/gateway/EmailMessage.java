package com.wallet.walletservice.service.notification.gateway;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class EmailMessage {
    String toEmail;
    String subject;
    String htmlContent;
}
