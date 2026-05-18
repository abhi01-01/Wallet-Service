package com.wallet.walletservice.service.wallet.support;

import com.wallet.walletservice.domain.entity.AssetType;
import com.wallet.walletservice.domain.entity.Wallet;
import com.wallet.walletservice.domain.enums.OwnerType;
import com.wallet.walletservice.exception.AssetTypeNotFoundException;
import com.wallet.walletservice.repository.AssetTypeRepository;
import com.wallet.walletservice.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
@Slf4j
public class WalletProvider {

    public static final String SYSTEM_TREASURY = "SYSTEM_TREASURY";

    private final WalletRepository walletRepository;
    private final AssetTypeRepository assetTypeRepository;

    public Wallet findOrCreate(String ownerId, String assetCode) {
        return walletRepository.findByOwnerIdAndAssetCode(ownerId, assetCode)
                .orElseGet(() -> createWallet(ownerId, assetCode));
    }

    /*
     * Lazy initialization keeps wallet creation close to the asset lookup rule.
     * saveAndFlush intentionally executes the insert before the transfer engine
     * attempts SELECT FOR UPDATE locks on the participating wallet rows.
     */
    private Wallet createWallet(String ownerId, String assetCode) {
        AssetType assetType = assetTypeRepository.findByCode(assetCode)
                .orElseThrow(() -> new AssetTypeNotFoundException(assetCode));

        OwnerType type = ownerId.equals(SYSTEM_TREASURY) ? OwnerType.SYSTEM : OwnerType.USER;

        Wallet newWallet = Wallet.builder()
                .ownerId(ownerId)
                .ownerType(type)
                .assetType(assetType)
                .balance(BigDecimal.ZERO)
                .build();

        log.info("Lazily initialized new 0-balance wallet for owner={} with asset={}", ownerId, assetCode);
        return walletRepository.saveAndFlush(newWallet);
    }
}
