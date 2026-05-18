package com.wallet.walletservice.service.notification.gateway;

public interface EmailGateway {
    void send(EmailMessage message);
}
