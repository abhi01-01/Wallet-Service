package com.wallet.walletservice.domain.entity;

import com.wallet.walletservice.domain.enums.PaymentOrderStatus;
import io.hypersistence.utils.hibernate.id.Tsid;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "payment_orders")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@Builder
public class PaymentOrder {

    @Id
    @Tsid
    private Long id;

    @Column(name = "user_id", nullable = false, length = 100)
    private String userId;

    @Column(name = "razorpay_order_id", nullable = false, unique = true, length = 255)
    private String razorpayOrderId;

    @Column(name = "razorpay_payment_id", unique = true, length = 255)
    private String razorpayPaymentId;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "asset_code", nullable = false, length = 20)
    private String assetCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentOrderStatus status;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}
