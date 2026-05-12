package com.wallet.walletservice.repository;

import com.wallet.walletservice.domain.entity.PaymentOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PaymentOrderRepository extends JpaRepository<PaymentOrder, Long> {
    Optional<PaymentOrder> findByRazorpayOrderId(String razorpayOrderId);
}
