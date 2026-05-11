package com.wallet.walletservice.repository;

import com.wallet.walletservice.domain.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    // Used to enforce idempotency before attempting any state mutation
    Optional<Transaction> findByIdempotencyKey(String idempotencyKey);
}
