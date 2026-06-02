# 💰 Wallet Service

A high-performance, financially rigorous digital wallet service built with Spring Boot 4.0.6. The service manages user wallets, supports multiple asset types, records every balance movement through a double-entry ledger, integrated with Razorpay so authenticated users can purchase wallet credits through real payment orders, and provides a secure session management system using JWT and Refresh Tokens.

* [Initial Requirement](https://drive.google.com/file/d/1PTFW5_xbD04Lx3RW_QgvM-MmMYp23y78/view?usp=sharing)
---

## 📌 What This Project Is

Wallet Service is a backend application for managing prepaid digital balances such as Gold Coins, Diamonds, and Loyalty Points. It is designed for products where users buy credits, receive promotional bonuses, and spend those credits inside an application.

The important design goal is that wallet balance must be fast to read, safe to update, and fully auditable. To achieve that, the project stores the current wallet balance on the `wallets` table for quick reads, while every financial movement is also written to immutable `ledger_entries`. The ledger is the source of truth for audit and reconciliation, and the wallet balance acts as an optimized projection of that ledger. 

The app now also includes payment integration. A user does not directly call the internal system top-up endpoint. Instead, the user creates a Razorpay order, completes payment on the client side, and sends the Razorpay payment details back to this service. The service verifies the Razorpay signature server-side, marks the payment order as paid, and then credits the wallet using the same internal double-entry wallet flow used by trusted system top-up. It also supports account lifecycle management, including secure logout and GDPR-compliant account closure.

---

## 🧭 How The Application Works

1. **User Authentication**: A user signs up using email and password, verifies via OTP (sent through Brevo), or logs in through Google OAuth2. The `AuthService` facade delegates to focused services — `EmailAuthService`, `GoogleAuthService`, `AuthSessionService`, and `AccountClosureService` — so each concern stays small and independently testable.
2. **Token Management**: `AuthSessionService` issues a short-lived **Access Token** (5 mins) and a long-lived **Refresh Token** (7 days) for secure session persistence and targeted device revocation.
3. **Security**: Protected wallet and payment endpoints use Spring Security and JWT authentication.
4. **Multi-Asset Support**: Each user can own one wallet per asset type, such as `GOLD`, `DIAMOND`, or `LOYALTY`.
5. **Strategy-Driven Wallet Operations**: Every wallet movement (`TOPUP`, `BONUS`, `SPEND`, `FORFEIT`) is implemented as a `WalletOperation` bean. The `WalletOperationRegistry` resolves the bean by `TransactionType`, builds a `TransferCommand`, and hands it to a shared `WalletTransferService`. Adding a new transfer type is one new bean — no facade changes.
6. **Double-Entry Ledger**: All operations resolve to a transfer between two wallets:
   - **Top-up / Bonus**: `SYSTEM_TREASURY` debits, user wallet credits.
   - **Spend / Forfeit**: User wallet debits, `SYSTEM_TREASURY` credits.
7. **Pluggable Policies**: Per-operation rules (e.g. `SufficientBalancePolicy` for `SPEND`) are injected as a list of `WalletTransferPolicy` checks executed *after* pessimistic locks are held — so balance validation never reads stale data.
8. **Auditable Transactions**: Every successful transfer creates one `transactions` row and exactly two `ledger_entries` rows (source of truth).
9. **Payment Integration**: The `payment` package is split into `order` (create), `verification` (verify + credit), `gateway` (Razorpay SDK abstraction), and `cleanup` (sweeper). The `PaymentGateway` interface lets the Razorpay implementation be swapped without touching business code.
10. **Webhook Reconciliation**: Razorpay events land in a transactional inbox via `WebhookIngestionService`, are picked up by `WebhookPollerJob`, and dispatched through `WebhookDispatcher` to the matching `WebhookHandlerStrategy` (currently `PaymentCapturedStrategy`).
11. **Account Lifecycle & GDPR**: `AccountClosureService` orchestrates closure — it triggers `FORFEIT` operations through the same transfer engine for any non-zero balances (with explicit consent), scrubs PII, marks the account `CLOSED`, and revokes all refresh tokens.

This separation keeps each concern small and replaceable. Razorpay integration answers, "Did real money payment succeed?" The wallet engine answers, "How should credits move inside our system after that?"

```mermaid
graph TD
    subgraph Client [Frontend Layer]
        UI[React / Mobile UI]
        RZP_SDK[Razorpay Client SDK]
    end

    subgraph Infrastructure [API Gateway Layer - Future]
        RL[Redis Rate Limiter]
        AuthZ[API Gateway]
    end

    subgraph Service [Wallet Service Core]
        subgraph AuthSub [Auth Subsystem]
            EmailAuth[EmailAuthService]
            GoogleAuth[GoogleAuthService]
            Session[AuthSessionService]
            Closure[AccountClosureService]
        end

        subgraph PaySub [Payment Subsystem]
            OrderSvc[PaymentOrderService]
            Verify[PaymentVerificationService]
            PayGw[PaymentGateway - Razorpay]
            Sweeper[PaymentSweeperJob]
        end

        subgraph WalSub [Wallet & Ledger Subsystem]
            Registry[WalletOperationRegistry]
            Ops[WalletOperation beans: TopUp / Bonus / Spend / Forfeit]
            Transfer[WalletTransferService]
            Policies[WalletTransferPolicy chain]
        end

        subgraph WhSub [Webhook Inbox Subsystem]
            Ingest[WebhookIngestionService]
            Poller[WebhookPollerJob]
            Disp[WebhookDispatcher]
            Strat[PaymentCapturedStrategy]
        end

        subgraph NotifSub [Notification Subsystem]
            EmailSvc[EmailNotificationService]
            EmailGw[EmailGateway - Brevo]
        end
    end

    subgraph Storage [Persistence & Eventing]
        DB[(PostgreSQL - ACID Ledger)]
        Cache[(Redis - Session/Idempotency - Future)]
        MQ[[Kafka - Event Sourcing - Future]]
    end

    subgraph External [External Gateways]
        RZP_API[Razorpay Server]
        Brevo[Brevo HTTP API]
    end

    %% Auth Flow
    UI --> AuthZ
    AuthZ --> EmailAuth
    AuthZ --> GoogleAuth
    AuthZ --> Session
    AuthZ --> Closure
    EmailAuth --> EmailSvc
    EmailSvc --> EmailGw
    EmailGw --> Brevo
    EmailAuth --> DB
    GoogleAuth --> DB
    Session --> DB
    Closure --> Registry

    %% Payment Flow
    UI -->|1. /create-order| OrderSvc
    OrderSvc -->|2. Server-to-Server| PayGw
    PayGw --> RZP_API
    UI -->|3. Pass order_id| RZP_SDK
    RZP_SDK <-->|4. PCI-DSS Secure Payment| RZP_API
    RZP_SDK -->|5. HMAC Signature| UI
    UI -->|6. /verify| Verify
    Verify --> Registry

    %% Webhook Flow
    RZP_API -->|Async notification| Ingest
    Ingest --> DB
    DB -.->|SKIP LOCKED| Poller
    Poller --> Disp
    Disp --> Strat
    Strat --> Registry

    %% Wallet Engine
    Registry --> Ops
    Ops --> Transfer
    Transfer --> Policies
    Transfer --> DB
    Transfer -.->|Future: emit ledger events| MQ

    %% Cleanup Flow
    Sweeper --> DB
    OrderSvc --> DB
    Verify --> DB
```


---

## 🏗 System Architecture & Entity Relationships

The following diagram shows users, authentication records, wallets, payment orders, transactions, and the immutable ledger trail.

```mermaid
erDiagram
    USER ||--o{ WALLET : "owns"
    USER ||--o{ OTP_CODE : "authenticates"
    USER ||--o{ PAYMENT_ORDER : "creates"
    USER ||--o{ REFRESH_TOKEN : "identifies session"
    ASSET_TYPE ||--o{ WALLET : "defines currency"
    WALLET ||--o{ LEDGER_ENTRY : "records balance changes"
    TRANSACTION ||--o{ LEDGER_ENTRY : "composed of"
    PAYMENT_ORDER ||..o| TRANSACTION : "credits wallet after verification"
    WEBHOOK_EVENT ||--o| PAYMENT_ORDER : "reconciles"

    USER {
        UUID id PK
        string email UK
        string password_hash
        string owner_type
        string provider
        string google_id
        boolean email_verified
        string account_status "ACTIVE | CLOSED"
        datetime closed_at
        datetime created_at
        datetime updated_at
    }

    WEBHOOK_EVENT {
        Long id PK
        string event_id UK
        string event_type
        string order_id FK
        string status "RECEIVED | PROCESSING | PROCESSED | FAILED"
        jsonb payload
        int processing_attempts
        string failure_reason
        datetime received_at
        datetime processed_at
    }

    REFRESH_TOKEN {
        Long id PK
        UUID user_id FK
        string token UK
        datetime expires_at
        datetime created_at
    }

    WALLET {
        Long id PK
        string owner_id
        string owner_type
        Long asset_type_id FK
        BigDecimal balance
        Long version
        datetime created_at
        datetime updated_at
    }

    ASSET_TYPE {
        Long id PK
        string name UK
        string code UK
        string description
        datetime created_at
    }

    PAYMENT_ORDER {
        Long id PK
        string user_id
        string razorpay_order_id UK
        string razorpay_payment_id UK
        BigDecimal amount
        string asset_code
        string status
        datetime created_at
        datetime updated_at
    }

    TRANSACTION {
        Long id PK
        string idempotency_key UK
        string transaction_type "TOPUP | BONUS | SPEND | FORFEIT"
        string description
        string status
        datetime created_at
    }

    LEDGER_ENTRY {
        Long id PK
        Long transaction_id FK
        Long wallet_id FK
        string entry_type
        BigDecimal amount
        BigDecimal balance_after
        datetime created_at
    }

    OTP_CODE {
        Long id PK
        UUID user_id FK
        string code
        datetime expires_at
        boolean used
        datetime created_at
    }
```

---

## 💳 Payment Integration Flow

Payment integration is built around Razorpay orders and server-side signature verification. The `payment` package is split by responsibility so each step has a single owner:

| Component                     | Role                                                                    |
|-------------------------------|-------------------------------------------------------------------------|
| `PaymentController`           | HTTP boundary for `/create-order`, `/verify`, `/order-status`           |
| `PaymentService`              | Thin transactional facade that fans out to the focused services below   |
| `PaymentOrderService`         | Creates Razorpay order + persists local `PaymentOrder` (`CREATED`)      |
| `PaymentGateway` (interface)  | Abstracts the external gateway — `RazorpayPaymentGateway` is the impl   |
| `PaymentVerificationService`  | Orchestrates verify: authz, signature, status transition, wallet credit |
| `PaymentSignatureVerifier`    | HMAC-SHA256 signature verification (server-side only)                   |
| `PaymentUserGuard`            | Refuses credit for `CLOSED` accounts                                    |
| `WalletCreditService`         | Builds `TopUpRequest` with key `rzp_{paymentId}` and calls `WalletService.topUp` |
| `PaymentCleanupService` + `PaymentSweeperJob` | Bulk-fail orders stuck in `CREATED` past TTL              |

```mermaid
sequenceDiagram
    autonumber
    participant User
    participant Client
    participant Ctrl as PaymentController
    participant Facade as PaymentService
    participant OrderSvc as PaymentOrderService
    participant Gw as PaymentGateway<br/>(Razorpay)
    participant Verify as PaymentVerificationService
    participant SigVer as PaymentSignatureVerifier
    participant Guard as PaymentUserGuard
    participant Credit as WalletCreditService
    participant Wallet as WalletService<br/>(Transfer Engine)
    participant DB as PostgreSQL
    participant RZP as Razorpay Server

    Note over User, RZP: Phase 1 — Order Creation
    User->>Client: Select asset + amount
    Client->>Ctrl: POST /api/v1/payments/create-order
    Ctrl->>Facade: createOrder(userId, req)
    Facade->>OrderSvc: createOrder(...)
    OrderSvc->>Gw: createOrder(amount, currency)
    Gw->>RZP: REST: orders.create
    RZP-->>Gw: razorpay_order_id
    OrderSvc->>DB: INSERT payment_orders (status=CREATED)
    OrderSvc-->>Client: { orderId, amount, currency, status=CREATED }

    Note over Client, RZP: Phase 2 — Client-side Checkout
    Client->>RZP: Open Razorpay Checkout
    RZP-->>Client: payment_id + razorpay_signature

    Note over Client, Wallet: Phase 3 — Verification + Wallet Credit
    Client->>Ctrl: POST /api/v1/payments/verify
    Ctrl->>Facade: verifyPayment(...)
    Facade->>Verify: verifyPayment(...)
    Verify->>DB: SELECT PaymentOrder by razorpay_order_id
    Verify->>Guard: ensureUserCanReceivePaymentCredit(userId)

    alt Order already PAID
        Verify-->>Ctrl: 200 OK (idempotent no-op)
    else New verification
        Verify->>SigVer: isValid(orderId, paymentId, signature)
        alt Signature invalid
            Verify->>DB: UPDATE status=FAILED
            Verify-->>Ctrl: 400 — Invalid signature
        else Signature valid
            Verify->>DB: UPDATE status=PAID, razorpay_payment_id
            Verify->>Credit: creditVerifiedPayment(order, paymentId)
            Credit->>Wallet: topUp(req, key="rzp_{paymentId}")
            Note right of Wallet: WalletOperationRegistry → TopUpWalletOperation<br/>→ WalletTransferService<br/>(pessimistic lock, 1 txn, 2 ledger entries)
            Wallet-->>Credit: TransactionResponse
            Verify-->>Ctrl: 200 OK
        end
    end
```

### Why Payment Orders Exist

`PaymentOrder` is the bridge between the external payment gateway and the internal wallet ledger. It records the payment intent before money is captured and keeps Razorpay identifiers separate from wallet accounting identifiers.

The status moves through:

- `CREATED`: Razorpay order was created and stored locally.
- `PAID`: Razorpay signature was verified and wallet credit was triggered.
- `FAILED`: Signature verification failed or sweeper expired the order.

This makes payment reconciliation easier because the system can answer whether a Razorpay order was created, whether it was verified, and whether the wallet was credited.

### Why Verification Is Split into Small Collaborators

`PaymentVerificationService` is intentionally a thin orchestrator that delegates to single-purpose collaborators (`PaymentSignatureVerifier`, `PaymentUserGuard`, `WalletCreditService`). Each collaborator can be unit-tested in isolation, and swapping the gateway later (Stripe, PayU, etc.) is a matter of providing alternative `PaymentGateway` / `PaymentSignatureVerifier` beans — no changes to controllers or the wallet engine.

### 🧹 Stale Payment Order Sweeper (Cron Job)

To prevent abandoned checkouts or orphaned payment intents from lingering indefinitely, `PaymentSweeperJob` runs every 15 minutes (`@Scheduled(cron = "0 0/15 * * * *")`).

It delegates to `PaymentCleanupService`, which executes a bulk database update to find any `PaymentOrder` stuck in the `CREATED` state past a 2-hour cutoff time and automatically transitions its status to `FAILED`. This ensures:
- The database remains clean of stale pending records.
- Financial reporting accurately reflects failed or abandoned conversion attempts.
- Late-arriving webhooks or client verifications are cleanly rejected if they exceed the payment time-to-live (TTL).

---



### 💳🪝 Payment Webhook Architecture

To ensure reliability even if the server restarts or Razorpay experiences issues, the service uses a **Transactional Inbox + Strategy Dispatcher** pattern. The pipeline is composed of four focused classes in `service/webhook/`:

| Class                       | Role                                                                            |
|-----------------------------|---------------------------------------------------------------------------------|
| `WebhookController`         | HTTP boundary, receives raw payload + signature header                          |
| `WebhookIngestionService`   | Verifies HMAC, deduplicates by `event_id`, persists `webhook_events` row        |
| `WebhookPollerJob`          | `@Scheduled(fixedDelay=500ms)` worker that claims rows via `FOR UPDATE SKIP LOCKED` |
| `WebhookDispatcher`         | Looks up the first `WebhookHandlerStrategy` whose `supports(eventType)` returns true |
| `WebhookHandlerStrategy`    | Interface — new event types are new beans, no dispatcher changes                |
| `PaymentCapturedStrategy`   | Concrete strategy for `payment.captured` events                                 |

```mermaid
sequenceDiagram
    autonumber
    participant RZP as Razorpay Gateway
    participant API as WebhookController
    participant IS as WebhookIngestionService
    participant DB as PostgreSQL (Inbox)
    participant Worker as WebhookPollerJob
    participant Disp as WebhookDispatcher
    participant Strat as PaymentCapturedStrategy
    participant Wallet as WalletService<br/>(Transfer Engine)

    Note over RZP, API: Phase 1 — High-Throughput Ingestion
    RZP->>API: POST /api/v1/webhooks/razorpay (Raw Payload + X-Razorpay-Signature)
    API->>IS: ingestRazorpayWebhook(rawPayload, signature)

    rect rgb(30, 30, 30)
    Note right of IS: Cryptographic Validation
    IS->>IS: Utils.verifyWebhookSignature(HMAC-SHA256)
    end

    IS->>DB: SELECT by event_id (dedupe check)
    alt Duplicate event_id
        IS-->>API: Skip (already ingested)
    else New event
        IS->>DB: INSERT webhook_events (JSONB, status='RECEIVED')
    end
    IS-->>API: Ingestion complete
    API-->>RZP: 200 OK (instant response)

    Note over DB, Wallet: Phase 2 — Async Idempotent Processing
    loop fixedDelay = 500 ms
        Worker->>DB: SELECT ... FOR UPDATE SKIP LOCKED<br/>(status='RECEIVED' AND attempts under MAX_ATTEMPTS)
        alt No event available
            DB-->>Worker: empty
        else Event claimed
            DB-->>Worker: WebhookEvent row locked
            Worker->>DB: UPDATE status='PROCESSING', attempts += 1

            Worker->>Disp: dispatch(event)
            Disp->>Disp: pick first strategy where supports(eventType)
            Disp->>Strat: process(event)

            Strat->>DB: SELECT PaymentOrder by razorpay_order_id

            alt PaymentOrder.status == 'PAID'<br/>(race won by client /verify)
                Strat->>Strat: Skip — already credited (idempotent)
            else PaymentOrder needs credit
                Strat->>DB: UPDATE payment_order SET status='PAID', razorpay_payment_id
                Strat->>Wallet: topUp(req, key="rzp_webhook_{paymentId}")
                Note right of Wallet: WalletOperationRegistry → TopUpWalletOperation<br/>→ WalletTransferService<br/>(pessimistic lock, 1 txn, 2 ledger entries)
                Wallet-->>Strat: success
            end

            alt Strategy succeeded
                Worker->>DB: UPDATE status='PROCESSED', processed_at=NOW()
            else Strategy threw
                Worker->>DB: UPDATE status='FAILED', failure_reason
                Note right of Worker: Re-tried until attempts == MAX_ATTEMPTS (3)
            end
        end
        Note right of Worker: Transaction commits → row lock released
    end
```

### Transactional Inbox for Guaranteed Reconciliation

Relying solely on client-side confirmation creates a critical vulnerability: if a user's browser crashes after payment but before the `/verify` call, funds are deducted without crediting the wallet. To ensure absolute ledger reconciliation, this project implements the **Transactional Inbox Pattern**:

*   **Atomic Ingestion:** Raw JSONB payloads are cryptographically verified (HMAC-SHA256) and immediately persisted as `RECEIVED`. This ensures a near-instant 200 OK response to Razorpay, preventing gateway timeouts.
*   **Scalable Processing:** `WebhookPollerJob` uses PostgreSQL's `SELECT FOR UPDATE SKIP LOCKED` to process events asynchronously and horizontally across service instances without lock contention.
*   **Bounded Retries:** Each event has a `processing_attempts` counter; the poller stops claiming rows that have already hit `MAX_ATTEMPTS = 3`, parking them as `FAILED` for manual reconciliation (a future DLQ hook).
*   **Idempotent Credits:** The strategy uses `rzp_webhook_{paymentId}` as the wallet idempotency key (distinct from `rzp_{paymentId}` used by the client-side `/verify` flow). Combined with the `PaymentOrder.status == PAID` short-circuit, the wallet is credited exactly once even if both paths race.
*   **Open for Extension:** Handling a new Razorpay event (`refund.processed`, `order.paid`, etc.) is just a new `WebhookHandlerStrategy` bean — the dispatcher and poller stay untouched.


---

## 🚀 Quick Start With Docker

### Prerequisites

- Docker
- Docker Compose

### Run Everything

```bash
docker-compose up --build
```

This starts PostgreSQL, runs Flyway migrations, and starts Wallet Service on:

```text
http://localhost:8080
```

### Swagger UI

Explore and test endpoints at:

```text
http://localhost:8080/swagger-ui.html
```

---

## 🛠 Local Development

### Prerequisites

- Java 17+
- PostgreSQL
- Maven wrapper included in the project

### Setup

1. Create a local PostgreSQL database named `walletdb`.
2. Copy `.env.example` to `.env`.
3. Configure database, JWT, Brevo API, Google OAuth, and Razorpay values.
4. Start the service:

```bash
./mvnw spring-boot:run
```

### Important Environment Variables

| Variable                     | Purpose                                                    |
|------------------------------|------------------------------------------------------------|
| `SPRING_DATASOURCE_USERNAME` | PostgreSQL username for local app connection               |
| `SPRING_DATASOURCE_PASSWORD` | PostgreSQL password for local app connection               |
| `JWT_SECRET`                 | Secret used to sign JWT access tokens                      |
| `BREVO_API_KEY`              | API key for Brevo HTTP email delivery                      |
| `BREVO_SENDER_EMAIL`         | Verified sender email address on Brevo                     |
| `MAIL_SENDER_NAME`           | Display name for the email sender                          |
| `GOOGLE_CLIENT_ID`           | Google OAuth client id                                     |
| `GOOGLE_CLIENT_SECRET`       | Google OAuth client secret                                 |
| `RAZORPAY_KEY_ID`            | Razorpay API key id                                        |
| `RAZORPAY_KEY_SECRET`        | Razorpay API key secret used for signature verification    |
| `AUTHORIZED_SYSTEM_IDS`      | Optional allow-list for sensitive system wallet operations |

* `.env.example` file consist a list of env varibales used in application.

---

## 📡 API Reference

### Auth API

| Method   | Endpoint                      | Access | Description                                                           |
|----------|-------------------------------|--------|-----------------------------------------------------------------------|
| `POST`   | `/api/v1/auth/signup`         | Public | Register with email and password                                      |
| `POST`   | `/api/v1/auth/verify-otp`     | Public | Verify OTP and receive tokens                                         |
| `POST`   | `/api/v1/auth/login`          | Public | Login and receive JWT + Refresh Token                                 |
| `POST`   | `/api/v1/auth/resend-otp`     | Public | Send a fresh OTP for email verification                               |
| `POST`   | `/api/v1/auth/google`         | Public | Login or signup using a Google ID token                               |
| `POST`   | `/api/v1/auth/refresh-token`  | Public | Exchange Refresh Token for new Access Token                           |
| `POST`   | `/api/v1/auth/logout`         | User   | Revoke a specific refresh token session                               |
| `POST`   | `/api/v1/auth/oauth2/success` | User   | Handles successful Google OAuth2 login and returns the generated JWT. |
| `DELETE` | `/api/v1/auth/close-account`  | User   | Close account, forfeit funds, and scrub data                          |

### Wallet API

| Method | Endpoint                           | Access      | Description                                  |
|--------|------------------------------------|-------------|----------------------------------------------|
| `GET`  | `/api/v1/wallets/{userId}/balance` | User        | Get all balances for authenticated user      |
| `POST` | `/api/v1/wallets/spend`            | User        | Spend credits from authenticated user wallet |
| `POST` | `/api/v1/wallets/topUp`            | System      | Credit a user wallet from treasury           |
| `POST` | `/api/v1/wallets/bonus`            | System      | Issue promotional credits from treasury      |
| `GET`  | `/api/v1/wallets/{userId}/ledger`  | User/System | View auditable ledger history                |

### Payment API

| Method | Endpoint                        | Access | Description                                   |
|--------|---------------------------------|--------|-----------------------------------------------|
| `POST` | `/api/v1/payments/create-order` | User   | Create Razorpay order and local payment order |
| `POST` | `/api/v1/payments/verify`       | User   | Verify signature and credit wallet            |
| `GET`  | `/api/v1/payments/order-status/{orderId}` | User   | Poll order status for safe client-side verification |


### Webhook API

| Method | Endpoint                    | Access         | Description                                         |
|--------|-----------------------------|----------------|-----------------------------------------------------|
| `POST` | `/api/v1/webhooks/razorpay` | Public/Gateway | Ingest and cryptographically verify Razorpay events |
---

## 🛡 Key Financial Features

### Double-Entry Ledger

Every wallet movement creates two ledger rows:

- A `DEBIT` entry for the wallet losing value.
- A `CREDIT` entry for the wallet receiving value.

This gives the system an auditable trail and makes it possible to reconstruct wallet balances from historical ledger entries. The current wallet balance is kept for performance, but the ledger explains how that balance was reached.

### Idempotency

Financial APIs use idempotency keys, so duplicate requests do not double-credit or double-spend funds.

For direct wallet top-ups, bonuses, and spends, the client or system provides an `idempotencyKey`. For Razorpay wallet credits, the service generates the wallet top-up idempotency key from the Razorpay payment id:

```text
rzp_{razorpayPaymentId}
```

This means repeated verification calls for the same paid Razorpay payment do not create multiple wallet credits.

### Dual-Token System
- **Access Tokens**: Short-lived (5 m) JWTs used for API authorization.
- **Refresh Tokens**: Long-lived (7d) database-backed tokens. This allows the system to revoke specific device sessions instantly (logout) without waiting for a JWT to expire.

### Lazy Wallet Initialization

If a wallet does not yet exist for a valid asset code, the service can initialize a zero-balance wallet just in time. This keeps onboarding simple while still preserving the invariant that each owner has at most one wallet per asset type.

---

## ⚙️ Choice Of Technology And Why

### Spring Boot 4

Spring Boot provides a strong foundation for REST APIs, configuration, validation, dependency injection, transaction management, and production-ready conventions. It is a good fit for wallet systems because financial operations need clear service boundaries, reliable transaction demarcation, and mature database integration.

### Spring Data JPA and Hibernate

JPA keeps persistence code readable while still allowing explicit locking where financial correctness requires it. The service uses repositories for normal lookup operations and `PESSIMISTIC_WRITE` locking for critical wallet balance updates.

### PostgreSQL

PostgreSQL is used because wallet data needs transactional guarantees, row-level locks, unique constraints, numeric precision, and reliable indexing. Features like `NUMERIC(20, 4)`, unique constraints, foreign keys, and `SELECT FOR UPDATE` style locking are important for money-like systems.

### Flyway

Flyway gives the schema a versioned migration history. That matters because database changes are part of application behavior in a wallet service, not just storage details. Tables, constraints, indexes, and seed data must evolve in a controlled way across environments.

### Spring Security, JWT, OAuth2, and Google ID Token Login

The app supports stateless JWT authentication for APIs, email OTP verification for local signup, and Google-based authentication for smoother user onboarding. This combination supports frontend applications while keeping protected wallet and payment APIs tied to authenticated principals.

### Razorpay Java SDK

Razorpay integration is handled server-side through the official Java SDK. The service creates orders through Razorpay and verifies payment signatures using the Razorpay secret. Signature verification stays on the backend because client-side verification would expose trust decisions to an untrusted environment.

### Lombok

Lombok reduces repetitive DTO, entity, and builder code. That keeps domain and request/response objects concise while preserving strongly typed Java models.

### Hypersistence TSID

TSID identifiers provide time-sortable unique ids. They are friendlier to database indexes than fully random identifiers and still avoid exposing predictable sequential ids in the same way as plain auto-increment ids.

### Springdoc OpenAPI

Swagger UI is included so developers can inspect and test the API contract quickly. This is especially useful for payment and wallet flows where request structure and authentication expectations must be clear.

---

## 🔐 Concurrency Strategy

Wallet systems fail when two requests update the same balance at the same time without coordination. This project handles concurrency at multiple layers.

### Database Transaction Boundary

Wallet mutations run inside Spring-managed transactions. A spend, bonus, or top-up is treated as one atomic unit: lock wallets, validate rules, update balances, save transaction, and save ledger entries. If any step fails, the transaction rolls back.

### Pessimistic Wallet Locks

The service uses `PESSIMISTIC_WRITE` locking when loading wallet rows for mutation. This maps to database-level row locking and prevents two concurrent transactions from modifying the same wallet balance at the same time.

This is intentionally conservative. For financial balance updates, waiting briefly is better than allowing race conditions that could overspend a wallet or double-apply credits.

### Consistent Lock Ordering

Every transfer touches two wallets: debit wallet and credit wallet. If two concurrent transfers lock those wallets in different orders, a deadlock can occur.

To avoid that, the service sorts wallet ids in ascending order before acquiring locks. That gives every concurrent transaction the same lock acquisition order and removes circular waits.

### Validate Under Lock

Spend balance checks happen after wallet rows are locked. This is critical because checking balance before locking can produce stale reads. The system only decides whether a user has enough balances once it owns the write lock for that wallet row.

### Optimistic Version Field

Wallet entities include a `version` field as a secondary safety mechanism. The main protection is pessimistic locking, but versioned entities provide another guardrail for detecting conflicting updates if persistence behavior changes in the future.

### Idempotency Keys

Locking handles simultaneous updates. Idempotency handles duplicate requests. Both are required.

For example, a payment verification request might be retried because of a client timeout. The system should not credit the wallet twice just because the client sent the same verification request twice. The payment flow handles that by:

- returning early when a `PaymentOrder` is already `PAID`;
- using `rzp_{razorpayPaymentId}` as the internal wallet top-up idempotency key.

---

## ✅ Technical Integrity Checklist

- [x] Double-entry ledger for auditable wallet movement.
- [x] Pessimistic write locking for balance safety.
- [x] Consistent wallet lock ordering to reduce deadlock risk.
- [x] Idempotent financial operation design.
- [x] Razorpay order creation and payment signature verification.
- [x] Payment order tracking through `CREATED`, `PAID`, and `FAILED`.
- [x] JWT-protected user wallet and payment APIs.
- [x] System-only top-up and bonus endpoints.
- [x] Flyway-managed schema migrations.
- [x] Swagger/OpenAPI documentation.
- [x] TSID-based entity ids.

---

## 🔮 Future Roadmap

- [ ] Add a Flyway migration dedicated to the `payment_orders` table if not already applied in the target environment.
- [x] Add Razorpay webhook handling for asynchronous reconciliation of paid, failed, and captured payment events.
- [x] Add payment order expiry and stale order cleanup.
- [x] Add a payment order status endpoint so clients can poll order state safely.
- [ ] Add refund support and reverse-ledger entries for failed fulfillment or customer refunds.
- [ ] Add stronger reconciliation reports between Razorpay payments, `payment_orders`, wallet `transactions`, and `ledger_entries`.
- [ ] Add integration tests for payment verification, duplicate verification, invalid signatures, and wallet credit idempotency.
- [ ] Add admin APIs for payment investigation and manual reconciliation.
- [ ] Add rate limiting for auth, payment creation, and verification endpoints.
- [x] Add webhook signature verification and replay protection.
- [ ] Add multi-currency payment support and configurable asset purchase rules.
- [ ] Add observability dashboards for payment success rate, ledger failures, lock wait time, and retry patterns.
