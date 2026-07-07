package com.wallet.walletservice.messaging.event;

public final class WalletMessagingConstants {

    private WalletMessagingConstants(){}

    public static final String SOURCE_SERVICE = "wallet-service";

    public static final String AGGREGATE_TYPE_TRANSACTION = "TRANSACTION";

    public static final String WALLET_TRANSACTION_EVENTS_TOPIC = "wallet.transaction.events.v1";

    public static final String WALLET_TRANSACTION_POSTED_EVENT = "wallet.transaction.posted.v1";

    public static final int WALLET_TRANSACTION_POSTED_SCHEMA_VERSION = 1;

    public static final String HEADER_EVENT_ID = "event_id";
    public static final String HEADER_EVENT_TYPE = "event_type";
    public static final String HEADER_SCHEMA_VERSION = "schema_version";
    public static final String HEADER_SOURCE = "source";
    public static final String HEADER_AGGREGATE_TYPE = "aggregate_type";
    public static final String HEADER_AGGREGATE_ID = "aggregate_id";
}
