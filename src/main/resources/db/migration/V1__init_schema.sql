-- 1. Asset Types (Gold Coins, diamond and Loyalty Points)

CREATE TABLE asset_types(
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(100) NOT NULL UNIQUE ,
    code        VARCHAR(20) NOT NULL UNIQUE ,
    description TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 2. Wallets (one per user per asset type)

CREATE TABLE wallets(
    id              BIGSERIAL PRIMARY KEY ,
    owner_id        VARCHAR(100) NOT NULL ,     -- user UUID/TSID or "SYSTEM_TREASURY"
    owner_type      VARCHAR(20) NOT NULL ,      -- "USER" | "SYSTEM"
    asset_type_id   BIGINT NOT NULL REFERENCES asset_types(id),
    balance         NUMERIC(20, 4) NOT NULL DEFAULT 0 CHECK ( balance >= 0 ),    -- last line of defence : Prevents negative balance
    version         BIGINT NOT NULL DEFAULT 0,      -- Used for optimistic locking fallback
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (owner_id, asset_type_id)
);

-- 3. Transactions (One record per business event)

CREATE TABLE transactions(
    id                  BIGSERIAL PRIMARY KEY ,
    idempotency_key     VARCHAR(255) NOT NULL UNIQUE ,      -- Prevents duplicate charges
    transaction_type    VARCHAR(30) NOT NULL ,              -- TOPUP | BONUS | SPEND
    description         TEXT,
    status              VARCHAR(20) NOT NULL DEFAULT 'SUCCESS',
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 4. Ledger Entries (double-entry: every txn has exactly 2 rows)

CREATE TABLE ledger_entries(
    id                  BIGSERIAL PRIMARY KEY ,
    transaction_id      BIGINT  NOT NULL REFERENCES transactions(id),
    wallet_id           BIGINT  NOT NULL REFERENCES wallets(id),
    entry_type          VARCHAR(10) NOT NULL ,                      -- DEBIT | CREDIT
    amount              NUMERIC(20, 4) NOT NULL check ( amount > 0 ),
    balance_after       NUMERIC(20, 4) NOT NULL ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);


-- Indexes for fast lookups and locking

CREATE INDEX idx_wallets_owner              ON wallets(owner_id);
CREATE INDEX idx_ledger_wallet              ON ledger_entries(wallet_id);
CREATE INDEX idx_ledger_txn                 ON ledger_entries(transaction_id);
CREATE INDEX idx_txn_idempotency            ON transactions(idempotency_key);