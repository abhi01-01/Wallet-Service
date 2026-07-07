# Domain model and ER diagrams

The database is the source of truth for accounts, wallets, ledger entries, payments, webhooks, outbox publishing, and Kafka audit records.

## Core ER diagram

```mermaid
erDiagram
    USER ||--o{ OTP_CODE : receives
    USER ||--o{ REFRESH_TOKEN : owns
    USER ||--o{ WALLET : owns
    USER ||--o{ PAYMENT_ORDER : creates
    ASSET_TYPE ||--o{ WALLET : defines
    WALLET ||--o{ LEDGER_ENTRY : records
    TRANSACTION ||--o{ LEDGER_ENTRY : contains
    TRANSACTION ||--o{ OUTBOX_EVENT : emits
    PAYMENT_ORDER ||..o| TRANSACTION : credits_via_topup
    WEBHOOK_EVENT ||..o| PAYMENT_ORDER : reconciles
    OUTBOX_EVENT ||..o{ KAFKA_EVENT_AUDIT : observed_as

    USER {
      uuid id PK
      string email UK
      string password_hash
      string owner_type
      string provider
      string google_id UK
      boolean email_verified
      string account_status
      timestamptz closed_at
      timestamptz created_at
      timestamptz updated_at
    }

    ASSET_TYPE {
      bigint id PK
      string code UK
      string name
      string description
      timestamptz created_at
    }

    WALLET {
      bigint id PK
      string owner_id
      string owner_type
      bigint asset_type_id FK
      numeric balance
      bigint version
      timestamptz created_at
      timestamptz updated_at
    }

    TRANSACTION {
      bigint id PK
      string idempotency_key UK
      string transaction_type
      string description
      string status
      timestamptz created_at
    }

    LEDGER_ENTRY {
      bigint id PK
      bigint transaction_id FK
      bigint wallet_id FK
      string entry_type
      numeric amount
      numeric balance_after
      timestamptz created_at
    }

    PAYMENT_ORDER {
      bigint id PK
      string user_id
      string razorpay_order_id UK
      string razorpay_payment_id UK
      numeric amount
      string asset_code
      string status
      timestamptz created_at
      timestamptz updated_at
    }

    WEBHOOK_EVENT {
      bigint id PK
      string event_id UK
      string event_type
      string order_id
      string status
      jsonb payload
      int processing_attempts
      string failure_reason
      timestamptz received_at
      timestamptz processed_at
      timestamptz updated_at
    }

    OUTBOX_EVENT {
      bigint id PK
      uuid event_id UK
      string aggregate_type
      string aggregate_id
      string event_type
      int schema_version
      string topic
      string event_key
      jsonb payload
      jsonb headers
      string status
      int publish_attempts
      timestamptz next_attempt_at
      string locked_by
      timestamptz locked_at
      string last_error
      timestamptz created_at
      timestamptz published_at
      timestamptz updated_at
    }

    KAFKA_EVENT_AUDIT {
      bigint id PK
      uuid event_id
      string event_type
      string topic
      int partition
      bigint offset
      string event_key
      jsonb payload
      jsonb headers
      timestamptz consumed_at
      timestamptz event_created_at
    }

    OTP_CODE {
        bigint id PK
        UUID user_id FK
        string code
        timestamptz expires_at
        boolean used
        timestamptz created_at
    }

    REFRESH_TOKEN {
        bigint id PK
        UUID user_id FK
        string token UK
        timestamptz expires_at
        timestamptz created_at
    }
```

## Entity responsibilities

| Entity/table        | Responsibility                                                                  |
|---------------------|---------------------------------------------------------------------------------|
| `user`              | Auth profile, email identity, owner type, provider, Google link, account status |
| `otp_code`          | Email verification OTPs with expiry and used state                              |
| `refresh_token`     | Database-backed session tokens; revocable and checked during refresh/logout     |
| `asset_type`        | Stable asset catalog: `GOLD`, `DIAMOND`, `LOYALTY`                              |
| `wallet`            | Current balance projection per owner and asset                                  |
| `transaction`       | Business-level wallet mutation event                                            |
| `ledger_entrie`     | Immutable debit/credit accounting rows                                          |
| `payment_order`     | Local Razorpay order and verification state                                     |
| `webhook_event`     | Verified Razorpay webhook inbox for async processing                            |
| `outbox_event`      | Committed domain events waiting for Kafka publish                               |
| `kafka_event_audit` | Consumed Kafka event audit trail for observability                              |
| `shedlock`          | Distributed scheduled-job lock table                                            |

## Wallet identity model

A user can have zero to three wallets, one per asset code. A balance response can therefore return an empty `wallets` array or up to three entries.

Example:

```json
{
  "data": {
    "userId": "de8e9ca5-607d-4c87-8772-636b48673f94",
    "wallets": [
      {
        "assetCode": "LOYALTY",
        "assetName": "Loyalty Points",
        "balance": 1098,
        "walletId": 860836359529890400
      },
      {
        "assetCode": "DIAMOND",
        "assetName": "Diamonds",
        "balance": 1999,
        "walletId": 861350215775380200
      },
      {
        "assetCode": "GOLD",
        "assetName": "Gold Coins",
        "balance": 497,
        "walletId": 861356007131931400
      }
    ]
  },
  "message": null,
  "success": true
}
```

## Ledger response model

```json
{
  "success": true,
  "message": "Operation successful",
  "data": [
    {
      "entryId": 100,
      "transactionId": 50,
      "transactionType": "SPEND",
      "entryType": "DEBIT",
      "amount": 10,
      "balanceAfter": 90,
      "createdAt": "2026-07-06T13:15:09.581Z"
    }
  ]
}
```

## Integrity constraints

| Constraint                                      | Purpose                                |
|-------------------------------------------------|----------------------------------------|
| `wallets.balance >= 0`                          | Prevent negative persisted balance     |
| `ledger_entries.amount > 0`                     | Prevent zero/negative ledger movements |
| unique `transactions.idempotency_key`           | Idempotent wallet mutations            |
| unique `outbox_events.event_id`                 | Idempotent outbox event identity       |
| unique `payment_orders.razorpay_order_id`       | Payment-order replay protection        |
| unique `payment_orders.razorpay_payment_id`     | Payment capture replay protection      |
| unique webhook event id                         | Webhook replay protection              |
| unique Kafka audit event/topic-partition-offset | Duplicate-consume protection           |

## Design note: balance projection vs. ledger truth

`wallets.balance` is a fast projection for reads. The ledger is the audit trail. If they disagree, treat it as a production incident and reconcile from ledger entries.
