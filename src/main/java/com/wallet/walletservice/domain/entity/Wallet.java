package com.wallet.walletservice.domain.entity;

import com.wallet.walletservice.domain.enums.OwnerType;
import io.hypersistence.utils.hibernate.id.Tsid;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "wallets")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Wallet {

    @Id
    @Tsid
    private Long id;

    @Column(name = "owner_id", nullable = false)
    private String ownerId ;

    @Enumerated(EnumType.STRING)
    @Column(name = "owner_type", nullable = false)
    private OwnerType ownerType ;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_type_id", nullable = false)
    private AssetType assetType;

    @Column(nullable = false, precision = 20, scale = 4)
    private BigDecimal balance;

    /**
     * Optimistic lock version — JPA increments this on every UPDATE.
     * Paired with pessimistic locking in service for extra safety.
     */
    @Version
    private Long version;

    @Column(name = "created_at", updatable = false, nullable = false)
    private OffsetDateTime createdAt ;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt ;

    @PrePersist
    protected void onCreate(){
        this.createdAt = OffsetDateTime.now() ;
        this.updatedAt = OffsetDateTime.now() ;
        if(this.balance == null) this.balance = BigDecimal.ZERO ;
    }

    @PreUpdate
    protected void onUpdate(){
        this.updatedAt = OffsetDateTime.now();
    }
}
