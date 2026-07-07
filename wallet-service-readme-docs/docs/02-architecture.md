# Architecture

Wallet Service uses a layered modular architecture inside one Spring Boot service. The system is intentionally a modular monolith: domain consistency is easier to enforce inside one database transaction, while module boundaries keep future microservice extraction possible.

## High-level topology

```mermaid
flowchart TB
    subgraph ClientLayer["Client Layer"]
      Web["wallet-web Next.js"]
      RazorpayCheckout["Razorpay Checkout JS"]
    end

    subgraph EdgeLayer["Edge Layer"]
      Gateway["api-gateway"]
      JWT["JWT validation"]
      RateLimit["Redis/Lua rate limiting"]
      CircuitBreaker["Circuit breaker"]
    end

    subgraph ServiceLayer["wallet-service"]
      Controllers["Controllers"]
      Auth["Auth module"]
      Wallet["Wallet/Ledger module"]
      Payment["Payment module"]
      Webhook["Webhook module"]
      Outbox["Kafka Outbox module"]
      Admin["Admin query module"]
      Notification["Notification module"]
    end

    subgraph DataLayer["Data Layer"]
      Postgres[(PostgreSQL)]
      Kafka[(Kafka)]
    end

    subgraph External["External Providers"]
      Google["Google Identity"]
      Razorpay["Razorpay"]
      Brevo["Brevo"]
      Observability["Prometheus / Grafana / Tempo"]
    end

    Web --> Gateway
    Gateway --> JWT
    JWT --> RateLimit
    RateLimit --> CircuitBreaker
    CircuitBreaker --> Controllers
    RazorpayCheckout <--> Razorpay

    Controllers --> Auth
    Controllers --> Wallet
    Controllers --> Payment
    Controllers --> Webhook
    Controllers --> Admin

    Auth --> Google
    Auth --> Notification
    Notification --> Brevo
    Payment --> Razorpay
    Webhook <-- Razorpay

    Auth --> Postgres
    Wallet --> Postgres
    Payment --> Postgres
    Webhook --> Postgres
    Outbox --> Postgres
    Outbox --> Kafka
    Admin --> Postgres
    Controllers --> Observability
```

## Package responsibility model

```mermaid
flowchart TB
    Controller["controller\nHTTP API boundaries"]
    DTO["dto\nrequest validation and response shapes"]
    Config["config\nsecurity, Kafka, OpenAPI, Jackson, ShedLock"]
    Domain["domain\nJPA entities and enums"]
    Repo["repository\nSpring Data queries and locks"]
    Auth["service.auth\nemail, Google, OTP, sessions, closure"]
    Wallet["service.wallet\noperations, policies, transfer engine"]
    Payment["service.payment\norders, verification, cleanup"]
    Webhook["service.webhook\ninbox, poller, dispatcher"]
    Messaging["messaging\noutbox, Kafka producer, consumer, audit"]
    Notification["service.notification\nBrevo email flow"]
    DB[(PostgreSQL)]
    Kafka[(Kafka)]

    Controller --> DTO
    Controller --> Auth
    Controller --> Wallet
    Controller --> Payment
    Controller --> Webhook
    Controller --> Messaging
    Auth --> Repo
    Wallet --> Repo
    Payment --> Repo
    Webhook --> Repo
    Messaging --> Repo
    Repo --> Domain
    Repo --> DB
    Messaging --> Kafka
    Auth --> Notification
    Config --> Controller
```

## Why modular monolith first

| Option                                         | Benefit                                                                          | Cost                                                                          | Decision |
|------------------------------------------------|----------------------------------------------------------------------------------|-------------------------------------------------------------------------------|----------|
| Modular monolith                               | Strong DB transaction boundaries, lower operational overhead, easier consistency | Larger codebase in one deployable                                             | Chosen   |
| Microservices immediately                      | Independent scaling and deployment                                               | Distributed transactions, more failure modes, more infrastructure             | Deferred |
| Database-only triggers for events              | No app-side publisher logic                                                      | Harder to test, DB-specific behavior, less domain context                     | Avoided  |
| Direct Kafka publish inside wallet transaction | Simple code path                                                                 | Kafka outage can break wallet mutation or create rollback/event inconsistency | Avoided  |
| Transactional outbox                           | DB commit is source of truth; Kafka publishing retries                           | Adds outbox table and publisher job                                           | Chosen   |

## Request path through the gateway

```mermaid
sequenceDiagram
    autonumber
    participant Browser
    participant Gateway as api-gateway
    participant Wallet as wallet-service
    participant DB as PostgreSQL

    Browser->>Gateway: Request with Bearer access token
    Gateway->>Gateway: Validate token, ownerType, route, rate limit
    Gateway->>Wallet: Forward with X-Gateway-Token, X-User-Id, X-User-Role
    Wallet->>Wallet: GatewayIngressGuardFilter validates internal secret
    Wallet->>Wallet: GatewayIdentityAuthenticationFilter hydrates principal
    Wallet->>Wallet: @PreAuthorize and explicit ownership checks
    Wallet->>DB: Execute use case
    DB-->>Wallet: Result
    Wallet-->>Gateway: ApiResponse
    Gateway-->>Browser: ApiResponse
```

## Internal consistency boundary

Wallet mutations, ledger rows, payment state changes, and outbox event creation are kept inside database transactions where possible. Kafka publishing and webhook processing are asynchronous because those are integration concerns, not transaction truth.

```mermaid
flowchart LR
    API["HTTP or internal service call"] --> Transaction["@Transactional boundary"]
    Transaction --> WalletRows["wallet rows locked"]
    Transaction --> TxRow["transactions row"]
    Transaction --> LedgerRows["ledger entries"]
    Transaction --> PaymentOrder["payment order update when relevant"]
    Transaction --> Outbox["outbox event"]
    Transaction --> Commit["DB commit"]
    Commit --> Async["async publisher / consumer jobs"]
```

## Service deployment relationship

Deploy the backend service before the gateway route that exposes new backend endpoints. The gateway does not route to endpoints that are unavailable in wallet-service.

Recommended sequence for a new backend feature:

1. Add wallet-service endpoint, tests, and migration.
2. Deploy wallet-service to staging/testing.
3. Add api-gateway route/rate-limit rule if needed.
4. Deploy gateway.
5. Wire wallet-web UI.
6. Promote branch from development to testing to production.
