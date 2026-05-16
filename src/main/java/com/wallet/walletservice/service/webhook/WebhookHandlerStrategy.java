package com.wallet.walletservice.service.webhook;

import com.wallet.walletservice.domain.entity.WebhookEvent;

public interface WebhookHandlerStrategy {

    /**
     * Determines if this strategy handles the specific event.
     */
    boolean supports(String eventType);

    /**
     * Executes the idempotent business logic.
     * The caller manages the transaction boundary to ensure
     * the status update and the ledger update commit atomically.
     */
    void process(WebhookEvent event);
}
