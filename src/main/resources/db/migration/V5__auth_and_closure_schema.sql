-- Step 1: User Account State
ALTER TABLE users
ADD COLUMN account_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
ADD COLUMN closed_at TIMESTAMPTZ;

CREATE INDEX idx_users_account_status ON users(account_status);

-- Step 2: Refresh Tokens
CREATE TABLE refresh_tokens (
                                id          BIGSERIAL PRIMARY KEY,
                                user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                                token       VARCHAR(255) NOT NULL UNIQUE,
                                expires_at  TIMESTAMPTZ NOT NULL,
                                created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_refresh_tokens_token ON refresh_tokens(token);
CREATE INDEX idx_refresh_tokens_user ON refresh_tokens(user_id);