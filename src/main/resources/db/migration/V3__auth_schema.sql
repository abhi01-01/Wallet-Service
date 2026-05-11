-- ============================================================
-- V3: Auth Schema — users + OTP codes
-- ============================================================

CREATE TABLE users(
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email               VARCHAR(255) NOT NULL UNIQUE ,
    password_hash       VARCHAR(255),                               -- NULL for Google-only accounts
    owner_type          VARCHAR(20) NOT NULL DEFAULT 'USER',        -- USER | SYSTEM
    provider            VARCHAR(20) NOT NULL,                       -- EMAIL | GOOGLE
    google_id           VARCHAR(255) UNIQUE,                        -- NULL for email accounts
    email_verified      BOOLEAN NOT NULL DEFAULT false,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);


CREATE TABLE otp_codes(
    id                  BIGSERIAL PRIMARY KEY ,
    user_id             UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    code                VARCHAR(6) NOT NULL ,
    expires_at          TIMESTAMPTZ NOT NULL ,
    used                BOOLEAN NOT NULL DEFAULT false,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_users_email    ON users(email);
CREATE INDEX idx_users_google   ON users(google_id);
CREATE INDEX idx_otp_user_id    ON otp_codes(user_id);