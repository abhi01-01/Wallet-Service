package com.wallet.walletservice.service.webhook;

import com.wallet.walletservice.domain.entity.WebhookEvent;
import com.wallet.walletservice.domain.enums.WebhookStatus;
import com.wallet.walletservice.exception.PaymentException;
import com.wallet.walletservice.repository.WebhookEventRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class WebhookPollerJob {

    private final WebhookEventRepository webhookEventRepository;
    private final WebhookDispatcher webhookDispatcher;
    private static final int MAX_ATTEMPTS = 3;

    @Scheduled(fixedDelay = 500)
    @Transactional
    public void pollAndProcess(){

        Optional<WebhookEvent> eventOpt = webhookEventRepository.findNextAvailableEvent(MAX_ATTEMPTS);

        if(eventOpt.isEmpty()){
            return;
        }

        WebhookEvent event = eventOpt.get();
        log.info("Worker acquired lock on event: {}", event.getEventId());

        try {
            event.setStatus(WebhookStatus.PROCESSING);
            event.setProcessingAttempts(event.getProcessingAttempts() + 1);

            // Delegate to the specific business logic strategy
            webhookDispatcher.dispatch(event);

            event.setStatus(WebhookStatus.PROCESSED);
            event.setProcessedAt(OffsetDateTime.now());
            log.info("Successfully processed webhook event: {}", event.getEventId());
        } catch (Exception e) {
            log.error("Failed to process webhook event: {}. Reason: {}", event.getEventId(), e.getMessage());
            event.setStatus(WebhookStatus.FAILED);
            event.setFailureReason(e.getMessage());
        }

        // Transaction commits here, releasing the row lock and saving the updated status.
        webhookEventRepository.save(event);
    }

}
