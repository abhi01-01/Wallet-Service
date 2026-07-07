# Configuration

Configuration is environment-driven. Secrets must come from deployment environment or local `.env`, not from committed files.

## Core application

| Env/property                    | Purpose                                            |
|---------------------------------|----------------------------------------------------|
| `SPRING_PROFILES_ACTIVE`        | Profile selection                                  |
| `SERVER_PORT`                   | Service port; usually `8081` in local service mode |
| `SPRING_DATASOURCE_URL`         | PostgreSQL JDBC URL                                |
| `SPRING_DATASOURCE_USERNAME`    | DB username                                        |
| `SPRING_DATASOURCE_PASSWORD`    | DB password                                        |
| `DB_POOL_MAX_SIZE`              | Hikari max pool size                               |
| `DB_POOL_MIN_IDLE`              | Hikari min idle                                    |
| `DB_POOL_CONNECTION_TIMEOUT_MS` | Connection acquisition timeout                     |
| `HIBERNATE_JDBC_FETCH_SIZE`     | Fetch size for query tuning                        |

## Gateway/security

| Env/property              | Purpose                                             |
|---------------------------|-----------------------------------------------------|
| `GATEWAY_INTERNAL_SECRET` | Secret expected in `X-Gateway-Token`                |
| `AUTHORIZED_SYSTEM_IDS`   | Optional allow-list for sensitive SYSTEM operations |
| `JWT_SECRET`              | Base64 secret for access token signing              |
| `JWT_EXPIRATION_ACCESS`   | Access-token lifetime                               |
| `JWT_EXPIRATION_REFRESH`  | Refresh-token lifetime                              |
| `GOOGLE_CLIENT_ID`        | Google token audience                               |
| `GOOGLE_CLIENT_SECRET`    | Google OAuth secret if legacy flow is used          |

JWT secret generation:

```bash
openssl rand -base64 64
```

## Razorpay

| Env/property              | Purpose                                                        |
|---------------------------|----------------------------------------------------------------|
| `RAZORPAY_KEY_ID`         | Razorpay API key id                                            |
| `RAZORPAY_KEY_SECRET`     | Razorpay API secret and checkout signature verification secret |
| `RAZORPAY_WEBHOOK_SECRET` | Razorpay webhook HMAC secret                                   |

`RAZORPAY_KEY_SECRET` and `RAZORPAY_WEBHOOK_SECRET` are different. Webhook signature validation uses the webhook secret configured in Razorpay Dashboard.

## Email

| Env/property         | Purpose               |
|----------------------|-----------------------|
| `BREVO_API_KEY`      | Brevo API key         |
| `BREVO_SENDER_EMAIL` | Verified sender email |
| `MAIL_SENDER_NAME`   | Sender display name   |

## Kafka

| Env/property                                    | Purpose                            |
|-------------------------------------------------|------------------------------------|
| `WALLET_KAFKA_ENABLED` or equivalent property   | Enable/disable Kafka integration   |
| `SPRING_KAFKA_BOOTSTRAP_SERVERS`                | Kafka brokers                      |
| `WALLET_KAFKA_TOPICS_WALLET_TRANSACTION_POSTED` | Topic for wallet transaction event |
| `WALLET_KAFKA_CONSUMER_GROUP_ID`                | Audit consumer group id            |
| `WALLET_OUTBOX_PUBLISHER_BATCH_SIZE`            | Outbox claim/publish batch size    |
| `WALLET_OUTBOX_PUBLISHER_MAX_ATTEMPTS`          | Attempts before DEAD state         |
| `WALLET_OUTBOX_PUBLISHER_STALE_LOCK_TIMEOUT`    | Recover old PUBLISHING rows        |

Exact property names match the current `application.yaml` and `@ConfigurationProperties` classes.

## Observability

| Env/property               | Purpose                                           |
|----------------------------|---------------------------------------------------|
| `ACTUATOR_OBSCURE_PATH`    | Production actuator base path                     |
| `OTLP_GRAFANA_URL`         | OTLP endpoint                                     |
| `OTLP_GRAFANA_AUTH_HEADER` | OTLP auth header                                  |
| Prometheus scrape config   | owned by `wallet-local-infra` or production infra |

## Local `.env` example

```properties
SPRING_PROFILES_ACTIVE=dev
SERVER_PORT=8081
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/walletdb
SPRING_DATASOURCE_USERNAME=wallet
SPRING_DATASOURCE_PASSWORD=wallet
GATEWAY_INTERNAL_SECRET=default-edge-secret-string-123
JWT_SECRET=<base64-secret>
JWT_EXPIRATION_ACCESS=900000
JWT_EXPIRATION_REFRESH=604800000
GOOGLE_CLIENT_ID=<client-id>.apps.googleusercontent.com
RAZORPAY_KEY_ID=rzp_test_xxx
RAZORPAY_KEY_SECRET=xxx
RAZORPAY_WEBHOOK_SECRET=xxx
BREVO_API_KEY=xxx
BREVO_SENDER_EMAIL=no-reply@example.com
MAIL_SENDER_NAME=Wallet Service
SPRING_KAFKA_BOOTSTRAP_SERVERS=localhost:9092
```

## Configuration validation principles

- Fail fast for required production secrets.
- Allow safe no-op email sending in local/dev only if explicitly accepted.
- Keep Kafka disable flag available for local troubleshooting.
- Do not default production secrets to test values.
- Keep gateway and wallet-service `GATEWAY_INTERNAL_SECRET` identical in local compose and deployment.
