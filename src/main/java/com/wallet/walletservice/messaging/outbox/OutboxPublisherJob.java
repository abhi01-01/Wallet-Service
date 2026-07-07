package com.wallet.walletservice.messaging.outbox;

import com.wallet.walletservice.messaging.kafka.KafkaPublishResult;
import com.wallet.walletservice.messaging.kafka.KafkaPublisher;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "wallet.outbox.publisher", name = "enabled", havingValue = "true")
public class OutboxPublisherJob {

    private final OutboxEventService outboxEventService;
    private final KafkaPublisher kafkaPublisher;

    @Value("${wallet.outbox.publisher.batch-size:50}")
    private int batchSize;

    private final String publisherId = resolvePublisherId();

    @PostConstruct
    void init(){
        log.info("Outbox publisher initialized. publisherId={}, batchSize={}", publisherId, batchSize);
    }

    @Scheduled(fixedDelayString = "${wallet.outbox.publisher.fixed-delay-ms:1000}")
    @SchedulerLock(name = "outboxPublisherJob", lockAtMostFor = "PT30S", lockAtLeastFor = "PT1S")
    public void publishPendingEvents(){
        if (batchSize <= 0) {
            log.warn("Outbox publisher skipped because batchSize is invalid: {}", batchSize);
            return;
        }

        int recovered = outboxEventService.recoverStalePublishingEvents();
        if (recovered > 0) {
            log.warn("Recovered {} stale PUBLISHING outbox event(s)", recovered);
        }

        List<OutboxEvent> events = outboxEventService.claimReadyToPublish(batchSize, publisherId);

        if (events.isEmpty()) {
            return;
        }

        log.info("Claimed {} outbox event(s) for Kafka publish. publisherId={}", events.size(), publisherId);

        for (OutboxEvent event : events) {
            publishSingleEvent(event);
        }
    }

    private void publishSingleEvent(OutboxEvent event){
        try {
            KafkaPublishResult result = kafkaPublisher.publish(event);

            outboxEventService.markPublished(event.getId());

            log.info(
                    "Outbox event published successfully. outboxId={}, eventId={}, eventType={}, topic={}, partition={}, offset={}",
                    event.getId(),
                    event.getEventId(),
                    event.getEventType(),
                    result.topic(),
                    result.partition(),
                    result.offset()
            );
        } catch (Exception ex) {
            outboxEventService.markFailed(event.getId(), ex);

            log.error(
                    "Outbox event publish failed. outboxId={}, eventId={}, eventType={}, topic={}",
                    event.getId(),
                    event.getEventId(),
                    event.getEventType(),
                    event.getTopic(),
                    ex
            );
        }
    }

    private static String resolvePublisherId(){
        return resolveHostName()
                + "_"
                + ManagementFactory.getRuntimeMXBean().getName()
                + "_"
                + UUID.randomUUID();
    }

    private static String resolveHostName(){
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            return "unknown-host";
        }
    }
}