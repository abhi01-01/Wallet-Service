package com.wallet.walletservice.controller.admin;

import com.wallet.walletservice.dto.response.messaging.*;
import com.wallet.walletservice.messaging.kafka.audit.KafkaEventAuditRepository;
import com.wallet.walletservice.messaging.outbox.OutboxEventRepository;
import com.wallet.walletservice.messaging.outbox.OutboxStatus;
import com.wallet.walletservice.service.admin.AdminMessagingSummaryService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/messaging")
public class AdminMessagingController {

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaEventAuditRepository kafkaEventAuditRepository;
    private final AdminMessagingSummaryService adminMessagingSummaryService;

    @GetMapping("/outbox-events")
    public PagedResponse<OutboxEventListItemResponse> getOutboxEvents(
            @RequestParam(required = false) OutboxStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
            ){
        PageRequest pageRequest = PageRequest.of(page,size);

        Page<OutboxEventListItemResponse> responsePage = (status == null
                ? outboxEventRepository.findAllByOrderByCreatedAtDesc(pageRequest)
                : outboxEventRepository.findByStatusOrderByCreatedAtDesc(status, pageRequest))
                .map(OutboxEventListItemResponse::from);

        return PagedResponse.from(responsePage);
    }

    @GetMapping("/outbox-events/{eventId}")
    public OutboxEventResponse getOutboxEventByEventId(@PathVariable UUID eventId){
        return outboxEventRepository.findByEventId(eventId)
                .map(OutboxEventResponse::from)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Outbox event not found: " + eventId
                ));
    }

    @GetMapping("/kafka-audit-events")
    public PagedResponse<KafkaEventAuditListItemResponse> getKafkaAuditEvents(
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String aggregateType,
            @RequestParam(required = false) String aggregateId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ){
        PageRequest pageRequest = PageRequest.of(page, size);

        Page<KafkaEventAuditListItemResponse> responsePage;

        if((aggregateType == null) != (aggregateId == null)){
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Both aggregateType and aggregateId must be provided together"
            );
        }

        if(aggregateType != null){
            responsePage = kafkaEventAuditRepository
                    .findByAggregateTypeAndAggregateIdOrderByConsumedAtDesc(
                            aggregateType,
                            aggregateId,
                            pageRequest
                    )
                    .map(KafkaEventAuditListItemResponse::from);
        } else if (eventType != null && !eventType.isBlank()) {
            responsePage = kafkaEventAuditRepository
                    .findByEventTypeOrderByConsumedAtDesc(eventType, pageRequest)
                    .map(KafkaEventAuditListItemResponse::from);
        }else {
            responsePage = kafkaEventAuditRepository
                    .findAllByOrderByConsumedAtDesc(pageRequest)
                    .map(KafkaEventAuditListItemResponse::from);
        }

        return PagedResponse.from(responsePage);
    }

    @GetMapping("/kafka-audit-events/{eventId}")
    public KafkaEventAuditResponse getKafkaAuditEventByEventId(@PathVariable UUID eventId){
        return kafkaEventAuditRepository.findByEventId(eventId)
                .map(KafkaEventAuditResponse::from)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Kafka audit event not found: " + eventId
                ));
    }

    @GetMapping("/summary")
    public MessagingSummaryResponse getMessagingSummary() {
        return adminMessagingSummaryService.getSummary();
    }

}