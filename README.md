# 💰 Wallet Service

A high-performance, financially rigorous digital wallet service built with Spring Boot 4. The service manages user wallets, supports multiple asset types, records every balance movement through a double-entry ledger, and now integrates with Razorpay so authenticated users can purchase wallet credits through real payment orders.

---

## 📌 What This Project Is

Wallet Service is a backend application for managing prepaid digital balances such as Gold Coins, Diamonds, and Loyalty Points. It is designed for products where users buy credits, receive promotional bonuses, and spend those credits inside an application.

The important design goal is that wallet balance must be fast to read, safe to update, and fully auditable. To achieve that, the project stores the current wallet balance on the `wallets` table for quick reads, while every financial movement is also written to immutable `ledger_entries`. The ledger is the source of truth for audit and reconciliation, and the wallet balance acts as an optimized projection of that ledger.

The app now also includes payment integration. A user does not directly call the internal system top-up endpoint. Instead, the user creates a Razorpay order, completes payment on the client side, and sends the Razorpay payment details back to this service. The service verifies the Razorpay signature server-side, marks the payment order as paid, and then credits the wallet using the same internal double-entry wallet flow used by trusted system top-ups.

---

## 🧭 How The Application Works

1. A user signs up using email and password, verifies OTP, or logs in through Google.
2. The service issues a JWT token after successful authentication.
3. Protected wallet and payment endpoints use Spring Security and JWT authentication.
4. Each user can own one wallet per asset type, such as `GOLD`, `DIAMOND`, or `LOYALTY`.
5. Wallet operations are modeled as financial transfers between two wallets:
   - Top-up: `SYSTEM_TREASURY` debits, user wallet credits.
   - Bonus: `SYSTEM_TREASURY` debits, user wallet credits.
   - Spend: user wallet debits, `SYSTEM_TREASURY` credits.
6. Every successful transfer creates one `transactions` row and exactly two `ledger_entries` rows.
7. Payment purchases use a `payment_orders` table to track the external Razorpay order lifecycle before wallet credit happens.

This separation keeps payment processing and wallet accounting clean. Razorpay integration answers the question, "Did real money payment succeed?" Wallet Service answers the question, "How should credits move inside our system after that payment succeeds?"

---

## 🏗 System Architecture & Entity Relationships

The following diagram shows users, authentication records, wallets, payment orders, transactions, and the immutable ledger trail.

```mermaid
erDiagram
    USER ||--o{ WALLET : "owns"
    USER ||--o{ OTP_CODE : "authenticates"
    USER ||--o{ PAYMENT_ORDER : "creates"
    ASSET_TYPE ||--o{ WALLET : "defines currency"
    WALLET ||--o{ LEDGER_ENTRY : "records balance changes"
    TRANSACTION ||--o{ LEDGER_ENTRY : "composed of"
    PAYMENT_ORDER ||..o| TRANSACTION : "credits wallet after verification"

    USER {
        UUID id PK
        string email UK
        string password_hash
        string owner_type
        string provider
        string google_id
        boolean email_verified
        datetime created_at
        datetime updated_at
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
        string transaction_type
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

Payment integration is built around Razorpay orders and server-side signature verification.

```mermaid
sequenceDiagram
    participant User
    participant Client
    participant WalletService
    participant Razorpay
    participant Database

    User->>Client: Select asset and amount
    Client->>WalletService: POST /api/v1/payments/create-order
    WalletService->>Razorpay: Create Razorpay order
    Razorpay-->>WalletService: razorpay_order_id
    WalletService->>Database: Save PAYMENT_ORDER as CREATED
    WalletService-->>Client: Return order id, amount, currency, status
    Client->>Razorpay: Complete checkout
    Razorpay-->>Client: payment id + signature
    Client->>WalletService: POST /api/v1/payments/verify
    WalletService->>WalletService: Verify Razorpay signature
    WalletService->>Database: Mark PAYMENT_ORDER as PAID
    WalletService->>WalletService: Internal wallet top-up with idempotency key rzp_{paymentId}
    WalletService->>Database: Save transaction + debit/credit ledger entries
    WalletService-->>Client: Payment verified and wallet credited
```

### Why Payment Orders Exist

`PaymentOrder` is the bridge between the external payment gateway and the internal wallet ledger. It records the payment intent before money is captured and keeps Razorpay identifiers separate from wallet accounting identifiers.

The status moves through:

- `CREATED`: Razorpay order was created and stored locally.
- `PAID`: Razorpay signature was verified and wallet credit was triggered.
- `FAILED`: Signature verification failed.

This makes payment reconciliation easier because the system can answer whether a Razorpay order was created, whether it was verified, and whether the wallet was credited.

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
3. Configure database, JWT, mail, Google OAuth, and Razorpay values.
4. Start the service:

```bash
./mvnw spring-boot:run
```

### Important Environment Variables

| Variable | Purpose |
|----------|---------|
| `SPRING_DATASOURCE_USERNAME` | PostgreSQL username for local app connection |
| `SPRING_DATASOURCE_PASSWORD` | PostgreSQL password for local app connection |
| `JWT_SECRET` | Secret used to sign JWT access tokens |
| `MAIL_USERNAME` | SMTP username for OTP email delivery |
| `MAIL_PASSWORD` | SMTP password or app password |
| `GOOGLE_CLIENT_ID` | Google OAuth client id |
| `GOOGLE_CLIENT_SECRET` | Google OAuth client secret |
| `RAZORPAY_KEY_ID` | Razorpay API key id |
| `RAZORPAY_KEY_SECRET` | Razorpay API key secret used for signature verification |
| `AUTHORIZED_SYSTEM_IDS` | Optional allow-list for sensitive system wallet operations |

---

## 📡 API Reference

### Auth API

| Method | Endpoint | Access | Description |
|--------|----------|--------|-------------|
| `POST` | `/api/v1/auth/signup` | Public | Register with email and password, then send OTP |
| `POST` | `/api/v1/auth/verify-otp` | Public | Verify OTP and receive JWT |
| `POST` | `/api/v1/auth/login` | Public | Login with email/password and receive JWT |
| `POST` | `/api/v1/auth/resend-otp` | Public | Send a fresh OTP for email verification |
| `POST` | `/api/v1/auth/google` | Public | Login or signup using a Google ID token |
| `POST` | `/api/v1/auth/oauth2/success` | Public | OAuth2 success landing that returns the generated JWT |

### Wallet API

| Method | Endpoint | Access | Description |
|--------|----------|--------|-------------|
| `GET` | `/api/v1/wallets/{userId}/balance` | User owns `{userId}` | Get all balances for the authenticated user |
| `POST` | `/api/v1/wallets/spend` | User | Spend credits from the authenticated user's wallet |
| `POST` | `/api/v1/wallets/topUp` | System | Credit a user's wallet from the system treasury |
| `POST` | `/api/v1/wallets/bonus` | System | Issue promotional credits from the system treasury |
| `GET` | `/api/v1/wallets/{userId}/ledger?assetCode=GOLD` | User owner or System | View ledger history for one user asset wallet |

### Payment API

| Method | Endpoint | Access | Description |
|--------|----------|--------|-------------|
| `POST` | `/api/v1/payments/create-order` | User | Create a Razorpay order and store a local payment order |
| `POST` | `/api/v1/payments/verify` | User | Verify Razorpay signature and credit the wallet after successful payment |

### Payment Request Examples

Create order:

```json
{
  "amount": 100,
  "assetCode": "GOLD"
}
```

The service converts the amount to Razorpay's smallest currency unit before creating the Razorpay order.

Verify payment:

```json
{
  "razorpayOrderId": "order_983hdks82",
  "razorpayPaymentId": "pay_NHD782hdjks",
  "razorpaySignature": "signature_293hdjsk..."
}
```

---

## 🛡 Key Financial Features

### Double-Entry Ledger

Every wallet movement creates two ledger rows:

- A `DEBIT` entry for the wallet losing value.
- A `CREDIT` entry for the wallet receiving value.

This gives the system an auditable trail and makes it possible to reconstruct wallet balances from historical ledger entries. The current wallet balance is kept for performance, but the ledger explains how that balance was reached.

### Idempotency

Financial APIs use idempotency keys so duplicate requests do not double-credit or double-spend funds.

For direct wallet top-ups, bonuses, and spends, the client or system provides an `idempotencyKey`. For Razorpay wallet credits, the service generates the wallet top-up idempotency key from the Razorpay payment id:

```text
rzp_{razorpayPaymentId}
```

This means repeated verification calls for the same paid Razorpay payment do not create multiple wallet credits.

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

Spend balance checks happen after wallet rows are locked. This is critical because checking balance before locking can produce stale reads. The system only decides whether a user has enough balance once it owns the write lock for that wallet row.

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
- [ ] Add Razorpay webhook handling for asynchronous reconciliation of paid, failed, and captured payment events.
- [ ] Add payment order expiry and stale order cleanup.
- [ ] Add a payment order status endpoint so clients can poll order state safely.
- [ ] Add refund support and reverse-ledger entries for failed fulfillment or customer refunds.
- [ ] Add stronger reconciliation reports between Razorpay payments, `payment_orders`, wallet `transactions`, and `ledger_entries`.
- [ ] Add integration tests for payment verification, duplicate verification, invalid signatures, and wallet credit idempotency.
- [ ] Add admin APIs for payment investigation and manual reconciliation.
- [ ] Add rate limiting for auth, payment creation, and verification endpoints.
- [ ] Add webhook signature verification and replay protection.
- [ ] Add multi-currency payment support and configurable asset purchase rules.
- [ ] Add observability dashboards for payment success rate, ledger failures, lock wait time, and retry patterns.
