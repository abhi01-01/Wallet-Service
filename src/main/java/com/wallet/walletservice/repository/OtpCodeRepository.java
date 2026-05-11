package com.wallet.walletservice.repository;

import com.wallet.walletservice.domain.entity.OtpCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface OtpCodeRepository extends JpaRepository<OtpCode, Long> {

    // Retrieves the most recently created, unused OTP for a specific user
    Optional<OtpCode> findTopByUserIdAndUsedFalseOrderByCreatedAtDesc(UUID userId);

    // Bulk invalidations for older OTPs
    @Modifying
    @Query("UPDATE OtpCode o SET o.used = true WHERE o.userId = :userId AND o.used = false")
    void invalidateAllForUser(@Param("userId") UUID userId);
}
