package com.wallet.walletservice.repository;

import com.wallet.walletservice.domain.entity.Wallet;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WalletRepository extends JpaRepository<Wallet, Long> {

    // Retrieves a specific user's wallet for a specific currency
    @Query("SELECT w FROM Wallet w WHERE w.ownerId = :ownerId AND w.assetType.code = :assetCode")
    Optional<Wallet> findByOwnerIdAndAssetCode(@Param("ownerId") String ownerId,
                                               @Param("assetCode") String assetCode);

    /**
     * Pessimistic write lock — blocks concurrent updates on the same wallet rows.
     * Deadlock avoidance: always call with IDs sorted ascending (lower ID first).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM Wallet w WHERE w.id IN :ids ORDER BY w.id ASC")
    List<Wallet> findAllByIdForUpdate(@Param("ids") List<Long> ids);

    @Query("SELECT w FROM Wallet w WHERE w.ownerId = :ownerId")
    List<Wallet> findAllByOwnerId(@Param("ownerId") String ownerId);
}
