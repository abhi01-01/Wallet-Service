package com.wallet.walletservice.repository;

import com.wallet.walletservice.domain.entity.PaymentOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Optional;

@Repository
public interface PaymentOrderRepository extends JpaRepository<PaymentOrder, Long> {
    Optional<PaymentOrder> findByRazorpayOrderId(String razorpayOrderId);
    Optional<PaymentOrder> findByRazorpayPaymentId(String razorpayPaymentId);

    // IODR-safe search
    Optional<PaymentOrder> findByRazorpayOrderIdAndUserId(String razorpayOrderId, String userId);

    // Bulk update for sweeper
    @Modifying
    @Query("UPDATE PaymentOrder p SET p.status = 'FAILED', p.updatedAt = :currentTime " +
            "WHERE p.status = 'CREATED' AND p.createdAt < :cutoffTime ")
    int failStaleOrders(@Param("cutoffTime")OffsetDateTime cutoffTime,
                        @Param("currentTime") OffsetDateTime currentTime);
}
