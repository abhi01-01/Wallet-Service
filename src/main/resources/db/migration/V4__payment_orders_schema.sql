-- ============================================================
-- V4: Payment orders for Razorpay wallet top-ups
-- ============================================================

CREATE TABLE payment_orders(
    id                  BIGINT PRIMARY KEY,
    user_id             VARCHAR(100) NOT NULL,
    razorpay_order_id   VARCHAR(255) NOT NULL UNIQUE,
    razorpay_payment_id VARCHAR(255) UNIQUE,
    amount              NUMERIC(19, 4) NOT NULL CHECK (amount > 0),
    asset_code          VARCHAR(20) NOT NULL,
    status              VARCHAR(20) NOT NULL CHECK (status IN ('CREATED', 'PAID', 'FAILED')),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_payment_orders_user_id ON payment_orders(user_id);
CREATE INDEX idx_payment_orders_status ON payment_orders(status);

-- Seed one non-production Razorpay order so schema validation and basic queries
-- have representative data in freshly migrated local/dev databases.
INSERT INTO payment_orders (
    id,
    user_id,
    razorpay_order_id,
    amount,
    asset_code,
    status
) VALUES (
    100000000000000001,
    'user-alice-001',
    'order_seed_wallet_topup_001',
    100.0000,
    'GOLD',
    'CREATED'
);
