# Contributing

This project accepts changes that preserve wallet correctness, security boundaries, and operational recoverability. Treat wallet balance movement as financial code: every mutation needs deterministic behavior, tests, and clear documentation.

## Development prerequisites

| Tool | Purpose |
|------|---------|
| Java 17 | Application runtime and test execution |
| Maven wrapper | Build, test, and run commands |
| PostgreSQL | Primary database for local integration runs |
| Kafka | Outbox publishing and audit consumer integration |
| Docker | Local dependency orchestration through `wallet-local-infra` |
| Razorpay test account | Payment order and webhook verification work |
| Brevo test sender | OTP email delivery work |

Use the Maven wrapper from the repository root:

```bash
./mvnw test
./mvnw spring-boot:run
```

## Local setup

1. Start local infrastructure from the companion infra repository.
2. Configure application secrets through environment variables or a local profile file that is not committed.
3. Run Flyway against an empty local database.
4. Start Wallet Service.
5. Send requests through the gateway when validating secured behavior.

Direct calls to Wallet Service require `X-Gateway-Token` unless the endpoint is an actuator health endpoint.

## Branch and pull request flow

Use short-lived branches from the active development branch.

```text
feature/<scope>
fix/<scope>
docs/<scope>
test/<scope>
```

Every pull request contains:

| Item | Requirement |
|------|-------------|
| Problem statement | Explain the defect, feature, or operational gap |
| Implementation summary | List the changed modules and the business impact |
| Test evidence | Include commands run and results |
| Migration note | Mention every Flyway migration added |
| Security note | Call out any endpoint, role, token, or gateway-boundary change |
| Documentation note | Link updated docs for changed runtime behavior |

## Code standards

- Keep controllers thin; place use-case logic in services.
- Keep wallet balance mutation inside `WalletTransferService`.
- Route every new wallet movement through a `WalletOperation`.
- Use domain-specific exceptions and map them in `GlobalExceptionHandler`.
- Validate request DTOs with Bean Validation annotations.
- Keep logs actionable and avoid logging secrets, tokens, OTP values, raw passwords, or full payment signatures.
- Keep scheduled jobs idempotent and safe to retry.
- Prefer explicit transaction boundaries for multi-row consistency.
- Use repository locking methods for balance-critical reads.
- Keep public API response shapes stable unless the change is intentional and documented.

## Security rules

- Preserve the gateway-first boundary.
- Do not trust `X-User-Id` or `X-User-Role` unless `GatewayIngressGuardFilter` has validated the gateway token.
- Keep production Swagger/OpenAPI disabled.
- Keep direct service access protected by `gateway.internal-secret`.
- Enforce user ownership at controller or service entry points before mutating data.
- Restrict SYSTEM-only actions with `ownerType=SYSTEM`.
- Store refresh tokens server-side and revoke them on logout and account closure.
- Never commit production secrets, API keys, webhook secrets, SMTP keys, JWT secrets, or gateway secrets.

## Wallet and ledger rules

Wallet changes have stricter requirements than ordinary CRUD changes.

- A successful balance mutation creates exactly one `transactions` row.
- A successful balance mutation creates exactly two `ledger_entries` rows: one debit and one credit.
- Debit and credit amounts match.
- Wallet balances never become negative.
- Idempotency keys are deterministic for retried external events.
- Wallet row locks are acquired in sorted wallet-id order.
- Kafka events are emitted through the outbox, not directly from the transfer transaction.
- System wallets are seeded through Flyway and use stable owner ids.

## Payment rules

- Verify Razorpay checkout signatures server-side.
- Verify webhook signatures before storing webhook events.
- Keep browser verification and webhook recovery idempotent.
- Reject payment credits for closed users.
- Reject mismatched user/order ownership.
- Use Razorpay payment id as the stable source for payment-credit idempotency.
- Keep `LOYALTY` reward-only unless product requirements explicitly change the asset model.

## Kafka and outbox rules

- Insert outbox records in the same database transaction as the wallet transfer.
- Publish only records claimed from `outbox_events`.
- Mark publish attempts with status and failure metadata.
- Keep outbox publishing retryable.
- Persist Kafka audit events after successful consumption.
- Acknowledge Kafka records only after audit persistence succeeds.
- Keep event names and schema versions explicit.

## Flyway migration rules

- Add a new migration for every schema or seed-data change.
- Do not edit migrations that have already run in shared environments.
- Use idempotent seed inserts where repeatable local resets are common.
- Keep migration names descriptive and ordered.
- Confirm a fresh database can migrate from `V1` to the latest version.
- Confirm existing development data can migrate forward when data preservation matters.

For local databases that can be destroyed, drop the schema or database and rerun migrations. For shared databases, use `flyway repair` only after the team has agreed that the changed checksum represents the accepted migration history.

## Testing expectations

Run the narrowest useful test while developing, then run the full test suite before opening a pull request.

```bash
./mvnw test
```

Add tests for:

| Change type | Required coverage |
|-------------|-------------------|
| Wallet operation | Successful transfer, insufficient balance, idempotency |
| Transfer policy | Passing and failing policy cases |
| Controller endpoint | Authorization, validation, and error response |
| Payment flow | Signature success/failure, duplicate payment id, ownership mismatch |
| Webhook strategy | Duplicate event, already-paid order, failed processing |
| Outbox publisher | Published, failed, stale publishing recovery |
| Kafka consumer | Audit persistence and duplicate handling |
| Migration | Fresh database migration in local or CI environment |

## Documentation expectations

Update documentation in the same pull request when code changes runtime behavior.

| Code change | Documentation file |
|-------------|--------------------|
| Endpoint behavior | `docs/11-api-reference.md` |
| Security/access rule | `docs/03-runtime-security-and-access-control.md` |
| Wallet transfer behavior | `docs/05-wallet-ledger-engine.md` |
| Payment behavior | `docs/06-payments-razorpay.md` |
| Webhook behavior | `docs/07-webhook-reconciliation.md` |
| Kafka/outbox behavior | `docs/08-kafka-outbox-and-audit.md` |
| Migration behavior | `docs/12-persistence-and-migrations.md` |
| Config behavior | `docs/13-configuration.md` |
| Operational behavior | `docs/17-operational-runbook.md` |

Keep documentation direct and product-grade. Use definitive language, state actual behavior, and remove placeholder phrasing.

## Review checklist

- [ ] Code compiles.
- [ ] Tests pass.
- [ ] New runtime behavior has focused tests.
- [ ] Flyway migrations are additive and ordered.
- [ ] Wallet balance invariants remain intact.
- [ ] Idempotency keys exist for retryable external flows.
- [ ] Logs avoid secrets and noisy recurring messages.
- [ ] Security boundary remains gateway-first.
- [ ] Production profile still requires real secrets.
- [ ] Documentation matches the implemented behavior.
