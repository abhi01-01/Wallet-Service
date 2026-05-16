package com.wallet.walletservice.service.webhook;

import com.wallet.walletservice.domain.entity.WebhookEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class WebhookDispatcher {

    // Spring automatically populates this list with all beans implementing the interface
    private final List<WebhookHandlerStrategy> strategies;

    public void dispatch(WebhookEvent event){
        WebhookHandlerStrategy strategy = strategies.stream()
                .filter(s -> s.supports(event.getEventType()))
                .findFirst()
                .orElse(null);

        if (strategy == null) {
            log.warn("Ignored unhandled webhook event type: {}", event.getEventType());
            return;
        }

        log.info("Dispatching event_id={} to strategy={}", event.getEventId(), strategy.getClass().getSimpleName());
        strategy.process(event);
    }
}
