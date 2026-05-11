package com.wallet.walletservice.repository;

import com.wallet.walletservice.domain.entity.AssetType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AssetTypeRepository extends JpaRepository<AssetType, Long> {
    Optional<AssetType> findByCode(String code);
}
