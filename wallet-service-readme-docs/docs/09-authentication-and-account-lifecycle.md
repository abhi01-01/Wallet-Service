# Authentication and account lifecycle

Auth supports email/password with OTP verification, Google ID token login, refresh-token sessions, logout, and USER account closure.

## Auth module map

```mermaid
flowchart TB
    AuthController["AuthController"] --> AuthService["AuthService facade"]
    AuthService --> EmailAuth["EmailAuthService"]
    AuthService --> GoogleAuth["GoogleAuthService"]
    AuthService --> Session["AuthSessionService"]
    AuthService --> Closure["AccountClosureService"]
    EmailAuth --> Otp["OtpService"]
    Otp --> EmailNotification["EmailNotificationService"]
    EmailNotification --> Brevo["BrevoEmailGateway"]
    GoogleAuth --> Verifier["GoogleIdentityVerifier"]
    GoogleAuth --> GoogleUser["GoogleUserService"]
    Closure --> Wallet["WalletService.handleAccountClosure"]
    EmailAuth --> DB[(users / otp_codes)]
    Session --> Tokens[(refresh_tokens)]
    Closure --> DB
```

## Signup and OTP flow

```mermaid
sequenceDiagram
    autonumber
    participant Client
    participant AuthController
    participant EmailAuth as EmailAuthService
    participant Otp as OtpService
    participant Mail as EmailNotificationService
    participant Session as AuthSessionService
    participant DB as PostgreSQL

    Client->>AuthController: POST /api/v1/auth/signup
    AuthController->>EmailAuth: signup(email, password)
    EmailAuth->>DB: create EMAIL user, email_verified=false
    EmailAuth->>Otp: sendOtp(user)
    Otp->>DB: insert otp_codes, expires_at
    Otp->>Mail: async email OTP
    AuthController-->>Client: signup message

    Client->>AuthController: POST /api/v1/auth/verify-otp
    AuthController->>EmailAuth: verifyOtp(email, otp)
    EmailAuth->>Otp: validate latest unused OTP
    Otp->>DB: mark OTP used, user verified
    EmailAuth->>Session: build auth response
    Session->>DB: revoke existing refresh tokens, insert new token
    AuthController-->>Client: accessToken + refreshToken
```

## Google login flow

The frontend uses Google Identity Services. The browser obtains a Google ID token and sends it to the backend.

```mermaid
sequenceDiagram
    autonumber
    participant Browser
    participant Google
    participant Gateway
    participant AuthController
    participant GoogleAuth
    participant Verifier as GoogleIdentityVerifier
    participant Session as AuthSessionService
    participant DB as PostgreSQL

    Browser->>Google: Sign in with Google
    Google-->>Browser: ID token
    Browser->>Gateway: POST /api/v1/auth/google { idToken }
    Gateway->>AuthController: forward public auth request
    AuthController->>GoogleAuth: login(idToken)
    GoogleAuth->>Verifier: verify token audience = GOOGLE_CLIENT_ID
    Verifier-->>GoogleAuth: google subject/email
    GoogleAuth->>DB: find by google id or email, link/create user
    GoogleAuth->>Session: issue access + refresh token
    Session->>DB: replace refresh token
    AuthController-->>Browser: AuthResponse
```

`POST /api/v1/auth/oauth2/success` is legacy if this flow is used.

## Refresh token persistence

Access tokens are short-lived. Refresh tokens are database-backed and revocable.

```mermaid
stateDiagram-v2
    [*] --> LoggedIn: login/OTP/Google returns tokens
    LoggedIn --> LoggedIn: access token valid
    LoggedIn --> LoggedIn: access expired, refresh succeeds
    LoggedIn --> LoggedOut: refresh expired/revoked/corrupt
    LoggedIn --> LoggedOut: logout deletes refresh token
    LoggedIn --> Closed: close account
    Closed --> LoggedOut: tokens cleared
```

Frontend strict session mode can call refresh on app boot to validate that the refresh session is still valid. If the refresh token is corrupt or revoked, frontend clears tokens and redirects to login.

## Logout

Logout deletes the provided refresh token only if it belongs to the authenticated principal.
## Account closure

Only `ownerType=USER` sees or calls close-account.

Endpoint:

```text
DELETE /api/v1/auth/close-account
```

Request body:

```json
{
  "confirmForfeitBalance": true
}
```

```mermaid
sequenceDiagram
    autonumber
    participant Client
    participant AuthController
    participant Closure as AccountClosureService
    participant Wallet as WalletService
    participant Transfer as WalletTransferService
    participant DB as PostgreSQL

    Client->>AuthController: DELETE /api/v1/auth/close-account
    AuthController->>Closure: closeAccount(userId, confirmForfeitBalance)
    Closure->>DB: load active USER
    Closure->>Wallet: handleAccountClosure
    Wallet->>DB: load all user wallets
    alt positive balances and confirmForfeitBalance=false
      Wallet-->>Closure: AccountClosureException 409
    else confirmed or no positive balance
      loop positive wallet
        Wallet->>Transfer: FORFEIT to SYSTEM_TREASURY
        Transfer->>DB: transaction + double-entry ledger + outbox
      end
      Closure->>DB: mark CLOSED, closed_at
      Closure->>DB: scrub PII: anonymize email, clear password/google id
      Closure->>DB: delete refresh tokens
    end
```

## Profile page display rule

A profile page can show:

| Field                   |                             Show? |
|-------------------------|----------------------------------:|
| LDAP derived from email |                               Yes |
| Email                   |                               Yes |
| ownerType               |                               Yes |
| userId / subject        | No, unless explicitly admin/debug |
| password hash           |                             Never |
| refresh token           |                             Never |
| google id               |                             Never |
