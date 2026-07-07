package com.wallet.walletservice.messaging.outbox;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "wallet.outbox")
public class OutboxProperties {

    private Publisher publisher = new Publisher();

    @Getter
    @Setter
    public static class Publisher {
        private boolean enabled = true;
        private int batchSize = 50;
        private long fixedDelayMs = 2_000L;
        private int maxAttempts = 5;
        private long retryDelayMs = 60_000L;
        private long staleLockTimeoutMs = 120_000L;
    }
}
