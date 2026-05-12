package com.wallet.walletservice.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class PaymentException extends RuntimeException {

    private final HttpStatus status;

    public PaymentException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }

    public PaymentException(String message, HttpStatus status, Throwable cause) {
        super(message, cause);
        this.status = status;
    }

}
