# Operational runbook

This runbook focuses on common production/debug situations.

## Wallet balance mismatch

Symptoms:

- UI balance does not match expected ledger sum.
- Negative balance attempt rejected.
- User reports missing credit or debit.

Steps:

1. Identify user id from support/admin lookup, not from UI display alone.
2. Fetch `/api/v1/wallets/{userId}/balance`.
3. Fetch ledger for each asset.
4. Compare `wallets.balance` with last `ledger_entries.balanceAfter` per wallet.
5. Inspect related `transactions` by idempotency key or payment id.
6. Inspect outbox event for the transaction.
7. If payment-related, inspect `payment_orders` and webhook inbox.

Do not manually update wallet balance without a compensating ledger transaction.

## Payment captured but wallet not credited

Likely causes:

- Frontend verification failed or browser closed.
- Webhook not configured or signature failed.
- Webhook inbox row failed processing.
- Payment order not found by Razorpay order id.

Steps:

1. Check Razorpay Dashboard for payment/order id.
2. Check `payment_orders` by `razorpay_order_id`.
3. Check `webhook_events` for matching order/payment id.
4. If webhook row is FAILED, inspect `failure_reason`.
5. If payment order is PAID but no ledger, inspect idempotency key and transaction rows.
6. If wallet credited but Kafka not published, inspect outbox.

## Outbox stuck

Symptoms:

- Admin messaging summary shows many `FAILED` or old `PENDING` rows.
- Kafka audit has no new events.

Steps:

1. Check Kafka broker health.
2. Check `SPRING_KAFKA_BOOTSTRAP_SERVERS`.
3. Inspect latest outbox detail: `last_error`, attempts, topic.
4. Check whether publisher job is enabled and scheduled.
5. Check stale `PUBLISHING` rows; verify recovery timeout.
6. If rows are DEAD, decide whether to replay/reset after root cause is fixed.

## Kafka audit empty

Possible causes:

- Consumer group not running.
- Wrong topic name.
- Payload/header deserialization failure.
- Audit DB write failure.
- Consumer acknowledges before persistence due to bug.

Steps:

1. Confirm outbox rows are `PUBLISHED`.
2. Confirm Kafka topic has messages.
3. Check consumer logs.
4. Check `kafka_event_audit` unique constraint conflicts.
5. Check manual ack behavior.

## Google login blocked

Error:

```text
Error 401: invalid_client
no registered origin
```

Fix:

1. In browser console, run `window.location.origin`.
2. Add that exact origin to Google Cloud OAuth Web Client authorized JavaScript origins.
3. Verify `NEXT_PUBLIC_GOOGLE_CLIENT_ID` in frontend matches backend `GOOGLE_CLIENT_ID`.
4. Restart frontend dev server.

## Direct access forbidden

Symptoms:

- Backend returns 403 direct access forbidden.
- Gateway calls fail but direct Postman calls work only with special header.

Steps:

1. Confirm gateway route forwards to wallet-service internal host/port.
2. Confirm gateway adds `X-Gateway-Token` default filter.
3. Confirm wallet-service env `GATEWAY_INTERNAL_SECRET` matches gateway env.
4. Do not expose wallet-service directly in production.

## Account closure support case

1. Confirm request was from USER, not SYSTEM.
2. If close failed with 409, check positive balances.
3. If user confirmed forfeiture, verify FORFEIT ledger entries exist.
4. Confirm user is marked CLOSED and refresh tokens deleted.
5. Confirm PII is scrubbed according to policy.

## Deployment order for API additions

1. Merge/deploy wallet-service changes.
2. Add/merge gateway route changes.
3. Update frontend to call the route.
4. Update local infra if new dependencies/ports/topics are needed.
5. Validate through gateway, not direct service URL.
