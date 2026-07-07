package com.wallet.walletservice.messaging.kafka;

public class KafkaPublishException extends RuntimeException{

    public KafkaPublishException(String message, Throwable cause){
        super(message, cause);
    }

    public KafkaPublishException(String message){
        super(message);
    }
}