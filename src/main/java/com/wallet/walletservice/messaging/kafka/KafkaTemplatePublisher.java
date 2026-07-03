package com.wallet.walletservice.messaging.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wallet.walletservice.messaging.outbox.OutboxEvent;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "wallet.kafka", name = "enabled", havingValue = "true", matchIfMissing = true)
public class KafkaTemplatePublisher implements KafkaPublisher{

    private static final Duration PUBLISH_TIMEOUT = Duration.ofSeconds(10);
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public KafkaTemplatePublisher(
            @Qualifier("walletJsonNodeKafkaTemplate") KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public KafkaPublishResult publish(OutboxEvent outboxEvent) {
        validate(outboxEvent);

        String payload = serializePayload(outboxEvent);

        ProducerRecord<String, String> record = new ProducerRecord<>(
                outboxEvent.getTopic(),
                null,
                outboxEvent.getEventKey(),
                payload
        );

        addHeaders(record.headers(), outboxEvent);

        try {
            SendResult<String, String> sendResult = kafkaTemplate.send(record)
                    .get(PUBLISH_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);

            RecordMetadata metadata = sendResult.getRecordMetadata();

            log.info(
                    "Published outbox event to Kafka. eventId={}, eventType={}, topic={}, partition={}, offset={}",
                    outboxEvent.getEventId(),
                    outboxEvent.getEventType(),
                    metadata.topic(),
                    metadata.partition(),
                    metadata.offset()
            );

            return new KafkaPublishResult(
                    metadata.topic(),
                    metadata.partition(),
                    metadata.offset(),
                    metadata.timestamp()
            );

        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new KafkaPublishException(
                    "Kafka publish interrupted for outbox event " + outboxEvent.getEventId(),
                    ex
            );
        }catch (ExecutionException | TimeoutException ex) {
            throw new KafkaPublishException(
                    "Kafka publish failed for outbox event " + outboxEvent.getEventId(),
                    ex
            );
        }

    }

    private String serializePayload(OutboxEvent outboxEvent) {
        try {
            JsonNode payload = outboxEvent.getPayload();

            if (payload == null || payload.isNull()) {
                throw new KafkaPublishException(
                        "Outbox event payload must be present for event " + outboxEvent.getEventId()
                );
            }

            if (payload.isTextual()) {
                return payload.asText();
            }

            return objectMapper.writeValueAsString(payload);

        } catch (JsonProcessingException ex) {
            throw new KafkaPublishException(
                    "Failed to serialize outbox event payload for event " + outboxEvent.getEventId(),
                    ex
            );
        }
    }

    private void addHeaders(Headers kafkaHeaders, OutboxEvent outboxEvent){
        addHeader(kafkaHeaders, "outbox_id", String.valueOf(outboxEvent.getId()));
        addHeader(kafkaHeaders, "event_id", String.valueOf(outboxEvent.getEventId()));
        addHeader(kafkaHeaders, "event_type", outboxEvent.getEventType());
        addHeader(kafkaHeaders, "schema_version", String.valueOf(outboxEvent.getSchemaVersion()));
        addHeader(kafkaHeaders, "aggregate_type", outboxEvent.getAggregateType());
        addHeader(kafkaHeaders, "aggregate_id", outboxEvent.getAggregateId());

        JsonNode headers = outboxEvent.getHeaders();
        if(headers == null || !headers.isObject()){
            return;
        }

        for (Map.Entry<String, JsonNode> field : headers.properties()){
            JsonNode value = field.getValue();

            if(value != null && !value.isNull()){
                addHeader(kafkaHeaders, field.getKey(), value.asText());
            }
        }
    }

    private void addHeader(Headers headers, String key, String value){
        if (!StringUtils.hasText(key) || value == null){
            return;
        }
        headers.remove(key);
        headers.add(new RecordHeader(key, value.getBytes(StandardCharsets.UTF_8)));
    }

    private void validate(OutboxEvent outboxEvent){
        if (outboxEvent == null) {
            throw new KafkaPublishException("Outbox event must be present for Kafka publish");
        }

        if (!StringUtils.hasText(outboxEvent.getTopic())) {
            throw new KafkaPublishException("Outbox event topic must be present for Kafka publish");
        }

        if (!StringUtils.hasText(outboxEvent.getEventKey())) {
            throw new KafkaPublishException("Outbox event key must be present for Kafka publish");
        }

        if (outboxEvent.getPayload() == null || outboxEvent.getPayload().isNull()) {
            throw new KafkaPublishException("Outbox event payload must be present for Kafka publish");
        }
    }
}