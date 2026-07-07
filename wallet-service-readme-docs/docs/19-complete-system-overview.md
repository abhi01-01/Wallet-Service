# Complete system architecture

This document explains how Wallet Service works as a complete system. It focuses on architecture, runtime flow, module interaction, transaction boundaries, and data movement across synchronous and asynchronous paths.

Topic-specific documentation stays in the dedicated files:

| Topic | Detailed file |
|-------|---------------|
| API contracts | [`11-api-reference.md`](11-api-reference.md) |
| Domain model and ER details | [`04-domain-model-and-er.md`](04-domain-model-and-er.md) |
| Wallet ledger rules | [`05-wallet-ledger-engine.md`](05-wallet-ledger-engine.md) |
| Payments | [`06-payments-razorpay.md`](06-payments-razorpay.md) |
| Webhooks | [`07-webhook-reconciliation.md`](07-webhook-reconciliation.md) |
| Kafka outbox and audit | [`08-kafka-outbox-and-audit.md`](08-kafka-outbox-and-audit.md) |
| Auth lifecycle | [`09-authentication-and-account-lifecycle.md`](09-authentication-and-account-lifecycle.md) |

## Architecture summary

Wallet Service is a modular monolith with a financial consistency core. Multiple entry points exist, but every balance-changing path converges on the same wallet transfer engine.

```text
External event or user action
        |
        v
Gateway security boundary
        |
        v
Controller and application service
        |
        v
Domain operation / strategy
        |
        v
WalletTransferService when value moves
        |
        v
PostgreSQL transaction: wallets + transaction + ledger + outbox
        |
        v
Async recovery and event publication jobs
```

The central runtime rule:

> PostgreSQL commits wallet truth. Kafka events, webhook processing, admin screens, and metrics are derived from committed database state.

## C4 context view

```mermaid
flowchart TB
    PersonUser["User"]
    PersonSystem["System Operator"]

    Web["wallet-web<br/>User and operator UI"]
    Gateway["api-gateway<br/>JWT validation, routing, rate limits, trusted headers"]
    WalletService["wallet-service<br/>Auth, wallets, payments, webhooks, ledger, messaging"]

    Postgres[("PostgreSQL<br/>source of truth")]
    Kafka[("Kafka<br/>domain event transport")]
    Razorpay["Razorpay<br/>payment orders, checkout, webhooks"]
    Google["Google Identity<br/>ID token verification"]
    Brevo["Brevo<br/>OTP email delivery"]
    Observability["Prometheus / Grafana / Tempo<br/>metrics and traces"]

    PersonUser --> Web
    PersonSystem --> Web
    Web --> Gateway
    Gateway --> WalletService

    WalletService --> Postgres
    WalletService --> Kafka
    WalletService --> Razorpay
    Razorpay --> Gateway
    WalletService --> Google
    WalletService --> Brevo
    WalletService --> Observability
```

Context responsibilities:

| System | Responsibility |
|--------|----------------|
| `wallet-web` | Presents signup, login, wallet, payment, ledger, and operator screens |
| `api-gateway` | Owns public routing, client JWT validation, rate limiting, and trusted identity headers |
| `wallet-service` | Owns business state, financial movement, auth state, payment state, webhook state, and outbox state |
| PostgreSQL | Stores committed identity, wallet, ledger, payment, webhook, outbox, audit, and lock data |
| Kafka | Carries committed wallet transaction events to downstream consumers |
| Razorpay | Creates payment orders, processes checkout, and emits payment webhooks |
| Google Identity | Verifies Google login tokens |
| Brevo | Sends OTP emails |
| Observability stack | Reads health, metrics, and traces |

## Container and deployment view

```mermaid
flowchart TB
    subgraph Internet["Public / client network"]
        Browser["Browser"]
        RazorpayWebhook["Razorpay webhook sender"]
    end

    subgraph Edge["Edge network"]
        Gateway["api-gateway"]
        GatewayPolicy["Route auth<br/>rate limit<br/>circuit breaker"]
    end

    subgraph AppRuntime["Application runtime"]
        WalletA["wallet-service instance A"]
        WalletB["wallet-service instance B"]
        SchedulerA["scheduled jobs on instance A"]
        SchedulerB["scheduled jobs on instance B"]
    end

    subgraph DataRuntime["Data and messaging runtime"]
        DB[("PostgreSQL")]
        Kafka[("Kafka cluster")]
        ShedLock[("shedlock table")]
        Outbox[("outbox_events")]
        Audit[("kafka_event_audit")]
    end

    subgraph Providers["External providers"]
        Razorpay["Razorpay API"]
        Google["Google Identity"]
        Brevo["Brevo API"]
    end

    Browser --> Gateway
    RazorpayWebhook --> Gateway
    Gateway --> GatewayPolicy
    GatewayPolicy --> WalletA
    GatewayPolicy --> WalletB

    WalletA --> DB
    WalletB --> DB
    WalletA --> Kafka
    WalletB --> Kafka
    WalletA --> Razorpay
    WalletA --> Google
    WalletA --> Brevo
    WalletB --> Razorpay
    WalletB --> Google
    WalletB --> Brevo

    SchedulerA --> ShedLock
    SchedulerB --> ShedLock
    SchedulerA --> Outbox
    SchedulerB --> Outbox
    Outbox --> DB
    SchedulerA --> Kafka
    SchedulerB --> Kafka
    Kafka --> Audit
```

Deployment behavior:

| Runtime concern | Architecture decision |
|-----------------|-----------------------|
| Horizontal instances | Multiple service instances can run behind the gateway |
| Scheduled jobs | ShedLock prevents duplicate scheduled job execution across instances |
| Wallet concurrency | Database row locks protect wallet rows during balance mutation |
| Kafka publishing | Outbox rows are claimed from PostgreSQL, then published to Kafka |
| Webhook retries | Webhook rows are claimed from PostgreSQL and retried independently of the HTTP request |
| Health checks | Platform health probes use actuator health without application JWT |

## Application module map

```mermaid
flowchart TB
    subgraph Boundary["Inbound boundary"]
        SecurityConfig["SecurityConfig"]
        IngressGuard["GatewayIngressGuardFilter"]
        IdentityFilter["GatewayIdentityAuthenticationFilter"]
        ExceptionHandler["GlobalExceptionHandler"]
    end

    subgraph Controllers["HTTP controllers"]
        AuthController["AuthController"]
        WalletController["WalletController"]
        PaymentController["PaymentController"]
        WebhookController["WebhookController"]
        AdminUserController["AdminUserController"]
        AdminMessagingController["AdminMessagingController"]
    end

    subgraph AuthModule["Auth module"]
        AuthService["AuthService"]
        EmailAuthService["EmailAuthService"]
        GoogleAuthService["GoogleAuthService"]
        OtpService["OtpService"]
        AuthSessionService["AuthSessionService"]
        AccountClosureService["AccountClosureService"]
    end

    subgraph WalletModule["Wallet and ledger module"]
        WalletService["WalletService"]
        OperationRegistry["WalletOperationRegistry"]
        Operations["TopUp / Bonus / Spend / Forfeit operations"]
        TransferService["WalletTransferService"]
        WalletProvider["WalletProvider"]
        TransferPolicy["WalletTransferPolicy"]
    end

    subgraph PaymentModule["Payment module"]
        PaymentService["PaymentService"]
        PaymentOrderService["PaymentOrderService"]
        VerificationService["PaymentVerificationService"]
        WalletCreditService["WalletCreditService"]
        PaymentCleanupService["PaymentCleanupService"]
        RazorpayGateway["RazorpayPaymentGateway"]
    end

    subgraph WebhookModule["Webhook module"]
        WebhookIngestionService["WebhookIngestionService"]
        WebhookPollerJob["WebhookPollerJob"]
        WebhookDispatcher["WebhookDispatcher"]
        PaymentCapturedStrategy["PaymentCapturedStrategy"]
    end

    subgraph MessagingModule["Messaging module"]
        OutboxEventService["OutboxEventService"]
        OutboxEventFactory["OutboxEventFactory"]
        OutboxPublisherJob["OutboxPublisherJob"]
        KafkaTemplatePublisher["KafkaTemplatePublisher"]
        WalletAuditConsumer["WalletTransactionAuditConsumer"]
        KafkaAuditService["KafkaEventAuditService"]
    end

    subgraph NotificationModule["Notification module"]
        EmailNotificationService["EmailNotificationService"]
        OtpRenderer["OtpEmailTemplateRenderer"]
        BrevoEmailGateway["BrevoEmailGateway"]
    end

    subgraph Persistence["Persistence"]
        Repositories["Spring Data repositories"]
        Entities["JPA entities and enums"]
        Migrations["Flyway migrations"]
        PostgreSQL[("PostgreSQL")]
    end

    SecurityConfig --> IngressGuard
    IngressGuard --> IdentityFilter
    IdentityFilter --> Controllers
    Controllers --> ExceptionHandler

    AuthController --> AuthModule
    WalletController --> WalletModule
    PaymentController --> PaymentModule
    WebhookController --> WebhookModule
    AdminUserController --> AuthModule
    AdminMessagingController --> MessagingModule

    AuthModule --> NotificationModule
    AuthModule --> WalletModule
    PaymentModule --> WalletModule
    WebhookModule --> PaymentModule
    WebhookModule --> WalletModule
    WalletModule --> MessagingModule

    AuthModule --> Persistence
    WalletModule --> Persistence
    PaymentModule --> Persistence
    WebhookModule --> Persistence
    MessagingModule --> Persistence
    NotificationModule --> BrevoEmailGateway
    Persistence --> PostgreSQL
```

Module direction rules:

| Direction | Meaning |
|-----------|---------|
| Controllers call services | HTTP code does not own business rules |
| Payment and webhook call wallet | External money confirmation becomes internal wallet movement |
| Auth closure calls wallet | Account closure uses wallet operation flow for forfeiture |
| Wallet calls outbox | Committed financial movement produces a committed event record |
| Outbox calls Kafka | Kafka publish happens after database commit |
| Notification stays outside auth core | Email transport can change without changing auth state rules |

## How the application works

The application is a set of entry points that converge into a small number of consistency boundaries.

```mermaid
flowchart TB
    AuthEntry["Auth requests<br/>signup, OTP, login, Google, refresh, logout, close"]
    WalletEntry["Wallet requests<br/>balance, ledger, topUp, bonus, spend"]
    PaymentEntry["Payment requests<br/>create order, verify, status"]
    WebhookEntry["Razorpay webhook<br/>payment.captured"]
    AdminEntry["Admin requests<br/>user options, messaging audit"]
    JobsEntry["Scheduled jobs<br/>webhook poller, payment sweeper, outbox publisher"]

    GatewayBoundary["Gateway and Spring Security boundary"]
    AppServices["Application services"]
    WalletCore["WalletTransferService<br/>financial consistency core"]
    DBTransaction["PostgreSQL transaction"]
    AsyncPipelines["Async pipelines<br/>webhook retry, outbox publish, Kafka audit"]

    AuthEntry --> GatewayBoundary
    WalletEntry --> GatewayBoundary
    PaymentEntry --> GatewayBoundary
    WebhookEntry --> GatewayBoundary
    AdminEntry --> GatewayBoundary

    GatewayBoundary --> AppServices
    JobsEntry --> AppServices

    AppServices --> WalletCore
    AppServices --> DBTransaction
    WalletCore --> DBTransaction
    DBTransaction --> AsyncPipelines
    AsyncPipelines --> DBTransaction
```

Runtime convergence:

| Entry point | Immediate module | Consistency boundary |
|-------------|------------------|----------------------|
| Email signup | Auth module | User and OTP transaction |
| OTP verification | Auth module | User verification and session creation |
| Google login | Auth module | User link/create and session creation |
| Wallet spend | Wallet module | Wallet transfer transaction |
| System bonus | Wallet module | Wallet transfer transaction |
| Razorpay verification | Payment module | Payment order update plus wallet transfer |
| Razorpay webhook | Webhook module | Inbox insert, then async payment order update plus wallet transfer |
| Account closure | Auth module | Forfeit transfer plus user closure and token revocation |
| Outbox publishing | Messaging module | Outbox claim/update around external Kafka publish |
| Kafka audit | Messaging module | Audit insert before manual Kafka acknowledgement |

## Request pipeline

```mermaid
sequenceDiagram
    autonumber
    participant Client
    participant Gateway
    participant Guard as GatewayIngressGuardFilter
    participant Identity as GatewayIdentityAuthenticationFilter
    participant Security as Spring Security
    participant Controller
    participant Service
    participant DB as PostgreSQL

    Client->>Gateway: HTTP request
    Gateway->>Gateway: Validate public route, JWT, rate limit, circuit breaker
    Gateway->>Guard: Forward with X-Gateway-Token
    Guard->>Guard: Reject if token is missing or invalid
    Guard->>Identity: Continue trusted internal request
    Identity->>Identity: Build principal from X-User-Id and X-User-Role
    Identity->>Security: Attach Authentication to SecurityContext
    Security->>Controller: Enforce URL and method authorization
    Controller->>Controller: Validate DTO and ownership parameters
    Controller->>Service: Execute use case
    Service->>DB: Read/write state inside transaction when required
    DB-->>Service: Result
    Service-->>Controller: DTO response
    Controller-->>Client: ApiResponse through gateway
```

Request pipeline responsibilities:

| Stage | Responsibility |
|-------|----------------|
| Gateway | Public authentication, routing policy, rate limiting, trusted headers |
| Ingress guard | Prevent direct backend bypass |
| Identity filter | Convert trusted gateway headers into Spring principal |
| Spring Security | Enforce role-level access |
| Controller | Validate request shape and path/user ownership |
| Service | Execute business use case |
| Repository/database | Enforce persistence constraints and locks |

## Financial write path

All balance mutations use the same architecture path. The caller changes, but the write model remains stable.

```mermaid
flowchart TB
    subgraph Entry["1. Entry point"]
        WalletAPI["Wallet API<br/>spend, bonus, topUp"]
        PaymentVerify["Payment verification<br/>verified Razorpay payment"]
        WebhookStrategy["Webhook strategy<br/>payment.captured recovery"]
        AccountClosure["Account closure<br/>forfeit positive balances"]
    end

    subgraph OperationPhase["2. Operation resolution"]
        Registry["WalletOperationRegistry"]
        Operation["WalletOperation<br/>TOPUP / BONUS / SPEND / FORFEIT"]
        Command["TransferCommand<br/>debit owner, credit owner, asset, amount, idempotency key"]
    end

    subgraph LockPhase["3. Consistency preparation"]
        Engine["WalletTransferService"]
        Idempotency["Check transactions.idempotency_key"]
        Resolve["Resolve debit and credit wallets"]
        SortedIds["Sort wallet ids"]
        Lock["Lock wallet rows<br/>PESSIMISTIC_WRITE"]
        Policy["Run transfer policies<br/>sufficient balance, operation rules"]
    end

    subgraph WritePhase["4. Atomic database write"]
        Debit["Debit source wallet"]
        Credit["Credit destination wallet"]
        TransactionRow["Insert transactions row"]
        LedgerRows["Insert debit and credit ledger_entries"]
        OutboxRow["Insert outbox_events row"]
        Commit["Commit PostgreSQL transaction"]
    end

    subgraph AsyncPhase["5. After commit"]
        OutboxPublisher["OutboxPublisherJob"]
        Kafka["Kafka wallet.transaction.posted"]
        Audit["Kafka audit consumer"]
    end

    WalletAPI --> Registry
    PaymentVerify --> Registry
    WebhookStrategy --> Registry
    AccountClosure --> Registry

    Registry --> Operation
    Operation --> Command
    Command --> Engine

    Engine --> Idempotency
    Idempotency --> Resolve
    Resolve --> SortedIds
    SortedIds --> Lock
    Lock --> Policy

    Policy --> Debit
    Debit --> Credit
    Credit --> TransactionRow
    TransactionRow --> LedgerRows
    LedgerRows --> OutboxRow
    OutboxRow --> Commit

    Commit --> OutboxPublisher
    OutboxPublisher --> Kafka
    Kafka --> Audit
```

The wallet write path is intentionally narrow. Payment, webhook, bonus, spend, and account closure do not update wallet balances directly. They express intent as a transfer command, then the engine performs idempotency, locking, balance mutation, ledger creation, and outbox recording.

### Financial transaction boundary

```mermaid
sequenceDiagram
    autonumber
    participant Service as Calling service
    participant Engine as WalletTransferService
    participant TxRepo as TransactionRepository
    participant Provider as WalletProvider
    participant WalletRepo as WalletRepository
    participant Policy as WalletTransferPolicy
    participant LedgerRepo as LedgerEntryRepository
    participant Outbox as OutboxEventService
    participant DB as PostgreSQL

    Service->>Engine: execute(TransferCommand)
    Engine->>TxRepo: findByIdempotencyKey(key)
    TxRepo->>DB: SELECT transactions
    Engine->>Provider: findOrCreate(debit owner, asset)
    Engine->>Provider: findOrCreate(credit owner, asset)
    Provider->>DB: SELECT/INSERT wallets
    Engine->>WalletRepo: findAllByIdForUpdate(sorted ids)
    WalletRepo->>DB: SELECT wallets FOR UPDATE
    Engine->>Policy: validate(command, locked wallets)
    Engine->>DB: UPDATE wallets SET balance, version
    Engine->>TxRepo: save(Transaction)
    TxRepo->>DB: INSERT transactions
    Engine->>LedgerRepo: saveAll(debit, credit)
    LedgerRepo->>DB: INSERT ledger_entries
    Engine->>Outbox: recordWalletTransactionPosted(transaction)
    Outbox->>DB: INSERT outbox_events
    DB-->>Service: Commit
```

Database objects touched by this boundary:

| Object | Why it is inside the transaction |
|--------|----------------------------------|
| `wallets` | Current balance projection changes atomically |
| `transactions` | Business-level transfer record commits with the balance |
| `ledger_entries` | Double-entry audit commits with the balance |
| `outbox_events` | Kafka publication intent commits only when the balance commits |

## Payment architecture path

Payment has a synchronous browser path and an asynchronous webhook recovery path. Both paths converge on wallet credit through the wallet engine.

```mermaid
flowchart TB
    subgraph CreateOrder["Create order path"]
        A1["POST /payments/create-order"] --> A2["PaymentOrderService"]
        A2 --> A3["RazorpayPaymentGateway.createOrder"]
        A3 --> A4["payment_orders row CREATED"]
        A4 --> A5["Return order id to client"]
    end

    subgraph BrowserVerify["Browser verification path"]
        B1["Client receives Razorpay payment id"] --> B2["POST /payments/verify"]
        B2 --> B3["PaymentVerificationService"]
        B3 --> B4["Validate order owner and status"]
        B4 --> B5["Verify Razorpay signature"]
        B5 --> B6["Mark payment order PAID"]
        B6 --> B7["WalletCreditService"]
        B7 --> B8["Wallet TOPUP transfer"]
    end

    subgraph WebhookRecovery["Webhook recovery path"]
        C1["Razorpay payment.captured webhook"] --> C2["WebhookIngestionService"]
        C2 --> C3["Verify webhook signature"]
        C3 --> C4["webhook_events row RECEIVED"]
        C4 --> C5["WebhookPollerJob"]
        C5 --> C6["PaymentCapturedStrategy"]
        C6 --> C7["Mark payment order PAID if unpaid"]
        C7 --> C8["Wallet TOPUP transfer"]
    end

    A5 --> B1
    A5 -. payment captured by Razorpay .-> C1
```

Important architectural point: the wallet engine does not know whether a top-up came from browser verification or webhook reconciliation. It receives a deterministic transfer request and applies the same idempotency, lock, ledger, and outbox rules.

## Webhook architecture path

```mermaid
sequenceDiagram
    autonumber
    participant Razorpay
    participant Gateway
    participant WebhookController
    participant Ingestion as WebhookIngestionService
    participant WebhookRepo as WebhookEventRepository
    participant Poller as WebhookPollerJob
    participant Dispatcher as WebhookDispatcher
    participant Strategy as PaymentCapturedStrategy
    participant PaymentRepo as PaymentOrderRepository
    participant Wallet as WalletService
    participant DB as PostgreSQL

    Razorpay->>Gateway: POST payment.captured + signature
    Gateway->>WebhookController: Forward webhook request
    WebhookController->>Ingestion: ingest(rawPayload, signature)
    Ingestion->>Ingestion: Verify Razorpay webhook signature
    Ingestion->>WebhookRepo: save RECEIVED event
    WebhookRepo->>DB: INSERT webhook_events
    WebhookController-->>Razorpay: 200 accepted

    Poller->>WebhookRepo: findNextAvailableEvent(MAX_ATTEMPTS)
    WebhookRepo->>DB: SELECT ... FOR UPDATE SKIP LOCKED
    Poller->>Dispatcher: dispatch(event)
    Dispatcher->>Strategy: handle payment.captured
    Strategy->>PaymentRepo: find by Razorpay order id
    Strategy->>PaymentRepo: mark PAID when unpaid
    Strategy->>Wallet: topUp(idempotencyKey)
    Wallet->>DB: wallet transfer transaction
    Strategy-->>Poller: success
    Poller->>WebhookRepo: mark PROCESSED
```

Webhook design separates three concerns:

| Concern | Owner |
|---------|-------|
| External authenticity | `WebhookIngestionService` verifies Razorpay signature |
| Durable acceptance | `webhook_events` stores the event before async processing |
| Business recovery | `PaymentCapturedStrategy` reconciles payment state and wallet credit |

## Outbox and Kafka architecture path

```mermaid
flowchart TB
    TransferCommit["Wallet transfer commits"]
    OutboxReady["outbox_events<br/>status READY"]
    Publisher["OutboxPublisherJob"]
    Claim["Claim batch<br/>READY -> PUBLISHING"]
    Publish["KafkaTemplatePublisher"]
    KafkaTopic["wallet.transaction.posted topic"]
    Published["Mark outbox PUBLISHED"]
    Failed["Mark outbox FAILED with reason"]
    Consumer["WalletTransactionAuditConsumer"]
    Audit["kafka_event_audit row"]
    Ack["Manual Kafka ack"]

    TransferCommit --> OutboxReady
    OutboxReady --> Publisher
    Publisher --> Claim
    Claim --> Publish
    Publish --> KafkaTopic
    Publish --> Failed
    KafkaTopic --> Published
    KafkaTopic --> Consumer
    Consumer --> Audit
    Audit --> Ack
```

Outbox is the architecture boundary between database truth and Kafka delivery.

```mermaid
sequenceDiagram
    autonumber
    participant Engine as WalletTransferService
    participant Outbox as OutboxEventService
    participant DB as PostgreSQL
    participant Publisher as OutboxPublisherJob
    participant Kafka as Kafka
    participant Consumer as WalletTransactionAuditConsumer
    participant Audit as KafkaEventAuditService

    Engine->>Outbox: recordWalletTransactionPosted(transaction)
    Outbox->>DB: INSERT outbox_events in transfer transaction
    DB-->>Engine: Commit

    Publisher->>DB: Recover stale PUBLISHING rows
    Publisher->>DB: Claim READY batch with SKIP LOCKED
    Publisher->>Kafka: publish event payload and headers
    Kafka-->>Publisher: broker acknowledgement
    Publisher->>DB: mark PUBLISHED

    Kafka->>Consumer: deliver wallet transaction event
    Consumer->>Audit: persist audit row
    Audit->>DB: INSERT kafka_event_audit
    Consumer->>Kafka: acknowledge offset
```

Failure behavior:

| Failure | Result | Recovery path |
|---------|--------|---------------|
| Transfer fails before commit | No outbox row exists | Nothing publishes |
| Service crashes after commit | Outbox row remains `READY` | Next publisher run claims it |
| Publisher crashes after claim | Row remains `PUBLISHING` | Stale recovery returns it to publishable state |
| Kafka send fails | Row records failure state and attempts | Later retry or operator inspection |
| Consumer crashes before ack | Kafka redelivers | Audit deduplication prevents duplicate audit records |

## Account closure architecture path

Account closure crosses auth and wallet domains. The architecture keeps financial movement in the wallet engine and identity cleanup in the auth module.

```mermaid
flowchart TB
    Request["POST /auth/close-account"]
    AuthCheck["Authenticated USER principal"]
    LoadUser["Load user and reject CLOSED"]
    BalanceCheck["Inspect wallet balances"]
    Forfeit["FORFEIT positive balances through WalletService"]
    WalletEngine["WalletTransferService"]
    Scrub["Scrub email, password hash, google id"]
    Close["Mark user CLOSED and closed_at"]
    Revoke["Delete refresh tokens"]
    Commit["Commit closure transaction"]

    Request --> AuthCheck
    AuthCheck --> LoadUser
    LoadUser --> BalanceCheck
    BalanceCheck --> Forfeit
    Forfeit --> WalletEngine
    WalletEngine --> Scrub
    Scrub --> Close
    Close --> Revoke
    Revoke --> Commit
```

This path prevents identity deletion from bypassing financial accounting. Positive balances move with ledger records before the account loses active identity data.

## Read model architecture

Read paths avoid wallet mutation and do not use the transfer engine.

```mermaid
flowchart LR
    Controller["Controller"]
    Authorization["Role and ownership check"]
    QueryService["Service query method"]
    Repository["Repository query"]
    Projection["DTO / response mapper"]
    Response["ApiResponse"]

    Controller --> Authorization
    Authorization --> QueryService
    QueryService --> Repository
    Repository --> Projection
    Projection --> Response
```

Read path examples:

| Read | Source |
|------|--------|
| Balance | `wallets` joined with `asset_types` |
| Ledger history | `ledger_entries`, `transactions`, `wallets`, `asset_types` |
| Payment order status | `payment_orders` filtered by order id and user |
| Admin user options | `users` query projection |
| Outbox admin view | `outbox_events` |
| Kafka audit admin view | `kafka_event_audit` |

## Database ownership view

```mermaid
flowchart TB
    subgraph AuthTables["Auth tables"]
        Users[("users")]
        Otp[("otp_codes")]
        Refresh[("refresh_tokens")]
    end

    subgraph WalletTables["Wallet and ledger tables"]
        Assets[("asset_types")]
        Wallets[("wallets")]
        Transactions[("transactions")]
        Ledger[("ledger_entries")]
    end

    subgraph PaymentTables["Payment and webhook tables"]
        Orders[("payment_orders")]
        WebhookEvents[("webhook_events")]
    end

    subgraph MessagingTables["Messaging tables"]
        OutboxEvents[("outbox_events")]
        KafkaAudit[("kafka_event_audit")]
    end

    subgraph OpsTables["Operations tables"]
        Shedlock[("shedlock")]
    end

    Users --> Otp
    Users --> Refresh
    Users --> Orders
    Assets --> Wallets
    Wallets --> Ledger
    Transactions --> Ledger
    Transactions --> OutboxEvents
    Orders --> WebhookEvents
    OutboxEvents --> KafkaAudit
```

Table ownership by module:

| Module | Tables it owns |
|--------|----------------|
| Auth | `users`, `otp_codes`, `refresh_tokens` |
| Wallet | `asset_types`, `wallets`, `transactions`, `ledger_entries` |
| Payment | `payment_orders` |
| Webhook | `webhook_events` |
| Messaging | `outbox_events`, `kafka_event_audit` |
| Scheduling | `shedlock` |

## Transaction and consistency map

```mermaid
flowchart TB
    subgraph StrongConsistency["Strong consistency inside PostgreSQL transaction"]
        UserSession["User/session state changes"]
        WalletTransfer["Wallet transfer + ledger + outbox"]
        PaymentVerify["Payment order paid + wallet top-up"]
        Closure["Account closure + forfeiture + token revocation"]
    end

    subgraph AsyncConsistency["Eventual consistency through durable tables"]
        WebhookInbox["Webhook inbox processing"]
        OutboxPublish["Outbox to Kafka"]
        KafkaAudit["Kafka consumption audit"]
        PaymentSweep["Stale payment cleanup"]
    end

    StrongConsistency --> AsyncConsistency
```

Consistency rules:

| Area | Consistency model |
|------|-------------------|
| Wallet balance | Strong consistency with database locks |
| Ledger audit | Strong consistency with balance mutation |
| Payment order verification | Strong consistency before wallet credit returns |
| Browser/webhook duplicate payment events | Idempotent convergence through deterministic keys |
| Kafka event delivery | Eventual consistency from outbox |
| Webhook processing | Eventual consistency from inbox |
| Admin messaging views | Read committed state from outbox and audit tables |

## Threading and background jobs

```mermaid
flowchart TB
    HttpThreads["HTTP request threads"]
    AsyncExecutor["Async executor"]
    Scheduler["Spring scheduler"]

    HttpThreads --> Controllers["Controllers"]
    Controllers --> Services["Application services"]

    Services --> AsyncExecutor
    AsyncExecutor --> Email["OTP email dispatch"]

    Scheduler --> WebhookPoller["WebhookPollerJob<br/>claim and process webhook_events"]
    Scheduler --> OutboxPublisher["OutboxPublisherJob<br/>claim and publish outbox_events"]
    Scheduler --> PaymentSweeper["PaymentSweeperJob<br/>expire stale payment_orders"]

    WebhookPoller --> ShedLock["ShedLock where configured"]
    OutboxPublisher --> ShedLock
    PaymentSweeper --> ShedLock
```

Job responsibilities:

| Job | Responsibility |
|-----|----------------|
| `WebhookPollerJob` | Convert accepted webhook rows into payment reconciliation attempts |
| `OutboxPublisherJob` | Convert committed outbox rows into Kafka messages |
| `PaymentSweeperJob` | Mark stale payment orders as failed |
| Async email dispatch | Send OTP mail without blocking the signup transaction longer than required |

## Architecture invariants

| Invariant | Architectural enforcement |
|-----------|---------------------------|
| Direct internet traffic does not reach business controllers | Gateway token guard blocks backend bypass |
| Header-based identity is trusted only after perimeter validation | Identity filter runs after ingress guard |
| Balance mutation has one implementation path | All financial operations use `WalletTransferService` |
| Ledger and wallet balance commit together | Same PostgreSQL transaction |
| Kafka reflects committed wallet state only | Outbox row is written in the wallet transaction and published later |
| Webhooks are durable before processing | Webhook endpoint writes inbox row before async dispatch |
| Scheduled jobs remain multi-instance safe | ShedLock and row-level claiming |
| Payment credit is idempotent | Razorpay payment id maps to deterministic wallet idempotency key |
| Account closure preserves accounting | Forfeiture uses wallet transfer before identity cleanup |

## Production runtime shape

```mermaid
flowchart TB
    subgraph EdgeRuntime["Edge runtime"]
        Gateway["api-gateway<br/>public API entry"]
        HealthProbe["Platform health probe"]
    end

    subgraph ReleaseConfig["Release configuration"]
        Image["wallet-service image / jar"]
        ProdProfile["prod profile"]
        Secrets["environment variables and secrets"]
    end

    subgraph WalletRuntime["Wallet Service runtime"]
        ServiceCluster["wallet-service deployment<br/>Instance A<br/>Instance B<br/>scheduled jobs"]
        Scheduler["job coordination<br/>ShedLock + row claims"]
    end

    subgraph CoreDependencies["Core dependencies"]
        Pg["PostgreSQL<br/>wallet truth and recovery queues"]
        Kfk["Kafka<br/>wallet events"]
    end

    subgraph ExternalIntegrations["External integrations"]
        Rzp["Razorpay"]
        Ggl["Google Identity"]
        Brv["Brevo"]
    end

    subgraph ObservabilityRuntime["Observability"]
        Health["/actuator/health"]
        Metrics["/actuator/prometheus"]
        Logs["application logs"]
        Otlp["OTLP collector"]
    end

    Gateway --> ServiceCluster
    HealthProbe --> Health

    Image --> ServiceCluster
    ProdProfile --> ServiceCluster
    Secrets --> ServiceCluster

    ServiceCluster --> Scheduler
    ServiceCluster --> Pg
    ServiceCluster --> Kfk
    Scheduler --> Pg
    Scheduler --> Kfk

    ServiceCluster --> Rzp
    ServiceCluster --> Ggl
    ServiceCluster --> Brv

    ServiceCluster --> Health
    ServiceCluster --> Metrics
    ServiceCluster --> Logs
    ServiceCluster --> Otlp
```

Production assumptions:

| Assumption | Reason |
|------------|--------|
| Gateway is the only public API entry point | Backend trusts gateway-supplied identity headers after token guard |
| PostgreSQL is highly available and backed up | It owns wallet truth and recovery queues |
| Kafka can be temporarily unavailable | Outbox stores pending events until publishing resumes |
| External providers can be temporarily unavailable | Payment, email, and Google failures stay outside wallet balance truth |
| Health endpoint remains low-noise and fast | Platform probes depend on it |

## Architecture reading order

Use this order when tracing a production issue:

1. Identify the entry point: auth, wallet, payment, webhook, admin, or scheduled job.
2. Follow the request boundary: gateway, ingress guard, identity filter, Spring Security, controller.
3. Locate the application service that owns orchestration.
4. For value movement, trace into `WalletTransferService`.
5. Check the PostgreSQL transaction tables involved in the path.
6. For asynchronous completion, inspect `webhook_events`, `outbox_events`, or `kafka_event_audit`.
7. Confirm metrics and logs at the boundary where the state stopped moving.
