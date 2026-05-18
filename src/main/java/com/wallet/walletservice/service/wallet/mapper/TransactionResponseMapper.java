package com.wallet.walletservice.service.wallet.mapper;

import com.wallet.walletservice.domain.entity.Transaction;
import com.wallet.walletservice.dto.response.TransactionResponse;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class TransactionResponseMapper {

    public TransactionResponse toResponse(Transaction txn) {
        List<TransactionResponse.LedgerEntryDto> entries = txn.getLedgerEntries().stream()
                .map(e -> TransactionResponse.LedgerEntryDto.builder()
                        .entryId(e.getId())
                        .walletId(e.getWallet().getId())
                        .ownerId(e.getWallet().getOwnerId())
                        .entryType(e.getEntryType().name())
                        .amount(e.getAmount())
                        .balanceAfter(e.getBalanceAfter())
                        .build())
                .collect(Collectors.toList());

        return TransactionResponse.builder()
                .transactionId(txn.getId())
                .idempotencyKey(txn.getIdempotencyKey())
                .type(txn.getTransactionType().name())
                .status(txn.getStatus().name())
                .description(txn.getDescription())
                .createdAt(txn.getCreatedAt())
                .ledgerEntries(entries)
                .build();
    }
}
