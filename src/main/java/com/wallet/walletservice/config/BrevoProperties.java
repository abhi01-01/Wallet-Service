package com.wallet.walletservice.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "brevo")
public class BrevoProperties {
    private Api api = new Api();

    @Data
    public static class Api {
        private String key;
        private String senderEmail;
        private String senderName;
    }
}
