package com.wallet.walletservice.exception;

/**
 * Thrown when an idempotency key is replayed with DIFFERENT parameters.
 * If parameters are identical, the original response is returned silently.
 */
public class DuplicateTransactionException extends RuntimeException{
    public DuplicateTransactionException(String key){
        super("Idempotency key already used with different parameters" + key);
    }
}
