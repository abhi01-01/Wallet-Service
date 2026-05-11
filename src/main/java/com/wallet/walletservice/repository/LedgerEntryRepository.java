package com.wallet.walletservice.repository;

import com.wallet.walletservice.domain.entity.LedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {
    @Query("SELECT le FROM LedgerEntry le WHERE le.wallet.id = :walletId ORDER BY le.createdAt DESC")
    List<LedgerEntry> findByWalletIdOrderByCreatedAtDesc(@Param("walletId") Long walletId);
}
