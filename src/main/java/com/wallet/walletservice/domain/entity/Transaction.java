package com.wallet.walletservice.domain.entity;

import com.wallet.walletservice.domain.enums.TransactionStatus;
import com.wallet.walletservice.domain.enums.TransactionType;
import io.hypersistence.utils.hibernate.id.Tsid;
import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "transactions")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Transaction {

    @Id
    @Tsid
    private Long id;

    /**
     * Unique key per business request.
     * If the same key is replayed, return the existing response — idempotent.
     */
    @Column(name = "idempotency_key", nullable = false, unique = true)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false)
    private TransactionType transactionType;

    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionStatus status;

    @Column(name = "created_at", updatable = false, nullable = false)
    private OffsetDateTime createdAt;

    @OneToMany(mappedBy = "transaction", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<LedgerEntry> ledgerEntries = new ArrayList<>();

    @PrePersist
    protected void onCreate(){
        this.createdAt = OffsetDateTime.now();
    }
}
