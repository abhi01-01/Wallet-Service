package com.wallet.walletservice.exception;

public class AssetTypeNotFoundException extends RuntimeException {
    public AssetTypeNotFoundException(String assetCode) {
        super(String.format("Invalid asset code: %s", assetCode));
    }
}
