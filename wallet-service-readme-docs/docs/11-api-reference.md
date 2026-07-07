# API reference

All normal responses use the service envelope:

```json
{
  "success": true,
  "message": "optional message",
  "data": {}
}
```

Errors use the same envelope where possible, with an appropriate HTTP status.

## Authentication

| Method   | Endpoint                      | Access                | Request                       | Response           |
|----------|-------------------------------|-----------------------|-------------------------------|--------------------|
| `POST`   | `/api/v1/auth/signup`         | Public behind gateway | `{ email, password }`         | Message            |
| `POST`   | `/api/v1/auth/verify-otp`     | Public behind gateway | `{ email, otp }`              | `AuthResponse`     |
| `POST`   | `/api/v1/auth/login`          | Public behind gateway | `{ email, password }`         | `AuthResponse`     |
| `POST`   | `/api/v1/auth/resend-otp`     | Public behind gateway | `{ email }`                   | Message            |
| `POST`   | `/api/v1/auth/google`         | Public behind gateway | `{ idToken }`                 | `AuthResponse`     |
| `POST`   | `/api/v1/auth/refresh-token`  | Public behind gateway | `{ refreshToken }`            | `AuthResponse`     |
| `POST`   | `/api/v1/auth/logout`         | USER/SYSTEM           | `{ refreshToken }`            | Message            |
| `DELETE` | `/api/v1/auth/close-account`  | USER only             | `{ confirmForfeitBalance }`   | Message            |
| `POST`   | `/api/v1/auth/oauth2/success` | Deprecated            | Legacy backend OAuth2 success | Legacy JWT wrapper |

`AuthResponse`:

```json
{
  "accessToken": "jwt",
  "refreshToken": "db-backed-refresh-token",
  "tokenType": "Bearer"
}
```

## Wallet balances

```text
GET /api/v1/wallets/{userId}/balance
```

Access:

- USER: only own `userId`.
- SYSTEM: target user selected through admin user options.

Response:

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
      }
    ]
  },
  "message": null,
  "success": true
}
```

## Ledger

```text
GET /api/v1/wallets/{userId}/ledger?assetCode=GOLD
```

Access:

- USER: own ledger only.
- SYSTEM: selected target user.

Response:

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

## Wallet actions

| Method | Endpoint | Access | Purpose |
|---|---|---|---|
| `POST` | `/api/v1/wallets/spend` | USER only | Debit user wallet and credit treasury |
| `POST` | `/api/v1/wallets/bonus` | SYSTEM only | Issue reward or promotional credits |
| `POST` | `/api/v1/wallets/topUp` | SYSTEM/internal only | Credit wallet from treasury; normally called by payment flow |

Spend request:

```json
{
  "userId": "user-uuid",
  "assetCode": "GOLD",
  "amount": 10,
  "idempotencyKey": "generated-key",
  "description": "Spend wallet credits"
}
```

Spend UI must not show `LOYALTY` in asset dropdown.

Bonus request:

```json
{
  "userId": "target-user-uuid",
  "assetCode": "LOYALTY",
  "amount": 100,
  "idempotencyKey": "generated-key",
  "description": "Campaign reward"
}
```

## Payments

| Method | Endpoint | Access | Purpose |
|---|---|---|---|
| `POST` | `/api/v1/payments/create-order` | USER only | Create Razorpay order |
| `POST` | `/api/v1/payments/verify` | USER only | Verify Razorpay signature and credit wallet |
| `GET` | `/api/v1/payments/order-status/{orderId}` | USER/SYSTEM | Inspect order status |

Create order request:

```json
{
  "assetCode": "GOLD",
  "amount": 100
}
```

Only `GOLD` and `DIAMOND` are exposed to the user for payment creation.

Verify request:

```json
{
  "razorpayOrderId": "order_...",
  "razorpayPaymentId": "pay_...",
  "razorpaySignature": "signature"
}
```

## Webhooks

```text
POST /api/v1/webhooks/razorpay
```

Headers:

```text
X-Razorpay-Signature: <signature>
```

Access:

- Public to Razorpay through gateway route.
- No app JWT.
- Must include gateway token when forwarded from gateway to service.
- Must verify Razorpay signature against raw body.

## Admin users

```text
GET /api/v1/admin/users/options?query=<optional>
```

Access: SYSTEM only.

Purpose: reusable dropdown for selecting target USER accounts.

Response:

```json
{
  "success": true,
  "data": [
    {
      "userId": "user-uuid",
      "email": "user@gmail.com",
      "ldap": "user",
      "ownerType": "USER"
    }
  ]
}
```

## Admin messaging

| Method | Endpoint | Access | Purpose |
|---|---|---|---|
| `GET` | `/api/v1/admin/messaging/summary` | SYSTEM | Outbox and Kafka audit summary |
| `GET` | `/api/v1/admin/messaging/outbox-events` | SYSTEM | Paginated compact outbox list |
| `GET` | `/api/v1/admin/messaging/outbox-events/{eventId}` | SYSTEM | Full outbox event detail |
| `GET` | `/api/v1/admin/messaging/kafka-audit-events` | SYSTEM | Paginated compact consumed event audit list |
| `GET` | `/api/v1/admin/messaging/kafka-audit-events/{eventId}` | SYSTEM | Full Kafka audit detail |

## Error handling

| Failure | Status |
|---|---|
| Validation error | `400` |
| Auth failure | `400` or `401` depending context |
| Google token invalid | `401` |
| Access denied | `403` |
| Wallet/user/payment not found | `404` |
| Positive balance close without confirmation | `409` |
| Duplicate/mismatched idempotency | `409` |
| Insufficient balance | `422` |
| External payment gateway unavailable | `502` or service-specific payment status |
| Unknown server error | `500` |
