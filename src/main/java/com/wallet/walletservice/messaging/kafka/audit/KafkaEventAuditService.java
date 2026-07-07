package com.wallet.walletservice.messaging.kafka.audit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class KafkaEventAuditService {

    private final KafkaEventAuditRepository kafkaEventAuditRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public void audit(ConsumerRecord<String, String> record){
        JsonNode payload = parsePayload(record.value());

        UUID eventId = requiredUuid(payload, "eventId");

        if (kafkaEventAuditRepository.existsByEventId(eventId)) {
            log.info("Skipping already audited Kafka event by eventId. eventId={}", eventId);
            return;
        }

        if (kafkaEventAuditRepository.existsByTopicAndPartitionIdAndEventOffset(
                record.topic(),
                record.partition(),
                record.offset()
        )) {
            log.info(
                    "Skipping already audited Kafka record. topic={}, partition={}, offset={}",
                    record.topic(),
                    record.partition(),
                    record.offset()
            );
            return;
        }

        KafkaEventAudit audit = KafkaEventAudit.builder()
                .eventId(eventId)
                .eventType(requiredText(payload, "eventType"))
                .schemaVersion(requiredInt(payload, "schemaVersion"))
                .source(requiredText(payload, "source"))
                .aggregateType(requiredText(payload, "aggregateType"))
                .aggregateId(requiredText(payload, "aggregateId"))
                .topic(record.topic())
                .partitionId(record.partition())
                .eventOffset(record.offset())
                .eventKey(record.key())
                .payload(payload)
                .headers(headersToJson(record))
                .build();

        try {
            kafkaEventAuditRepository.save(audit);
        }catch (DataIntegrityViolationException ex){
            /*
             * Another consumer instance may have inserted the same event before this transaction committed.
             * Treat duplicate audit insert as safe/idempotent.
             */
            log.warn(
                    "Kafka audit insert collided with existing unique key. eventId={}, topic={}, partition={}, offset={}",
                    eventId,
                    record.topic(),
                    record.partition(),
                    record.offset()
            );
        }

    }

    // Supporting Methods

    private JsonNode parsePayload(String value){
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException("Kafka event payload must not be blank");
        }

        try{
            JsonNode payload = objectMapper.readTree(value);

            if (!payload.isObject()) {
                throw new IllegalArgumentException("Kafka event payload must be a JSON object");
            }

            return payload;
        } catch (Exception ex) {
            throw new IllegalArgumentException("Failed to parse Kafka event payload", ex);
        }
    }

    private ObjectNode headersToJson(ConsumerRecord<String, String> record){
        ObjectNode headerNode = objectMapper.createObjectNode();

        for(Header header : record.headers()){
            String value = header.value() == null ? null : new String(header.value(), StandardCharsets.UTF_8);
            headerNode.put(header.key(), value);
        }

        return headerNode;
    }

    private int requiredInt(JsonNode payload, String fieldName){
        JsonNode node = payload.get(fieldName);

        if (node == null || node.isNull() || !node.canConvertToInt()) {
            throw new IllegalArgumentException("Kafka event missing required integer field: " + fieldName);
        }

        return node.asInt();
    }

    private String requiredText(JsonNode payload, String fieldName){
        JsonNode node = payload.get(fieldName);

        if (node == null || node.isNull() || !StringUtils.hasText(node.asText())) {
            throw new IllegalArgumentException("Kafka event missing required field: " + fieldName);
        }

        return node.asText();
    }

    private UUID requiredUuid(JsonNode payload, String fieldName){
        String value = requiredText(payload, fieldName);
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Kafka event field is not a valid UUID: " + fieldName, ex);
        }
    }

}
