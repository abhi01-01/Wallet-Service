package com.wallet.walletservice.service.webhook;

import com.wallet.walletservice.domain.entity.WebhookEvent;
import com.wallet.walletservice.domain.enums.WebhookStatus;
import com.wallet.walletservice.exception.PaymentException;
import com.wallet.walletservice.repository.WebhookEventRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
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

    // 1. Inject the Telemetry Engine
    private final MeterRegistry meterRegistry;

    @Scheduled(fixedDelay = 500)
    @Transactional
    public void pollAndProcess(){

        Optional<WebhookEvent> eventOpt = webhookEventRepository.findNextAvailableEvent(MAX_ATTEMPTS);

        if(eventOpt.isEmpty()){
            return;
        }

        WebhookEvent event = eventOpt.get();
        log.info("Worker acquired lock on event: {}", event.getEventId());

        // 2. Start the Timer ONLY when an event is actively processing
        Timer.Sample processTimer = Timer.start(meterRegistry);

        try {
            event.setStatus(WebhookStatus.PROCESSING);
            event.setProcessingAttempts(event.getProcessingAttempts() + 1);

            // Delegate to the specific business logic strategy
            webhookDispatcher.dispatch(event);

            event.setStatus(WebhookStatus.PROCESSED);
            event.setProcessedAt(OffsetDateTime.now());

            // 3. Symmetric Success Metric (Tags event type and attempt number)
            meterRegistry.counter("business.webhook.processing",
                    "event_type", event.getEventType(),
                    "status", "success",
                    "attempt", String.valueOf(event.getProcessingAttempts()),
                    "error", "none"
            ).increment();

            log.info("Successfully processed webhook event: {}", event.getEventId());

        } catch (Exception e) {

            meterRegistry.counter("business.webhook.processing",
                    "event_type", event.getEventType(),
                    "status", "failed",
                    "attempt", String.valueOf(event.getProcessingAttempts()),
                    "error", e.getClass().getSimpleName()
            ).increment();

            log.error("Failed to process webhook event: {}. Reason: {}", event.getEventId(), e.getMessage());
            event.setStatus(WebhookStatus.FAILED);
            event.setFailureReason(e.getMessage());

        }finally {
            // 5. Stop the Timer and bind it to the specific event type
            processTimer.stop(Timer.builder("business.webhook.latency")
                    .description("End-to-end latency for webhook processing")
                    .tag("event_type", event.getEventType())
                    .register(meterRegistry));
        }

        // Transaction commits here, releasing the row lock and saving the updated status.
        webhookEventRepository.save(event);
    }

}
