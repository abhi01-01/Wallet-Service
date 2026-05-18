package com.wallet.walletservice.service.notification.template;

public interface EmailTemplateRenderer<T> {
    String render(T model);
}
