-- Transactional Outbox for reliable Kafka event publishing.
-- Wallet ledger state remains the source of truth. This table stores events created
-- inside the same DB transaction as the wallet/ledger mutation.

CREATE TABLE outbox_events (
                               id                  BIGSERIAL PRIMARY KEY,
                               event_id            UUID NOT NULL UNIQUE,
                               aggregate_type      VARCHAR(80) NOT NULL,
                               aggregate_id        VARCHAR(120) NOT NULL,
                               event_type          VARCHAR(120) NOT NULL,
                               schema_version      INTEGER NOT NULL DEFAULT 1 CHECK (schema_version > 0),
                               topic               VARCHAR(150) NOT NULL,
                               event_key           VARCHAR(150) NOT NULL,
                               payload             JSONB NOT NULL,
                               headers             JSONB NOT NULL DEFAULT '{}'::jsonb,
                               status              VARCHAR(30) NOT NULL DEFAULT 'PENDING'
                                   CHECK (status IN ('PENDING', 'PUBLISHING', 'PUBLISHED', 'FAILED', 'DEAD')),
                               publish_attempts    INTEGER NOT NULL DEFAULT 0 CHECK (publish_attempts >= 0),
                               next_attempt_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                               locked_by           VARCHAR(120),
                               locked_at           TIMESTAMPTZ,
                               last_error          TEXT,
                               created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                               published_at        TIMESTAMPTZ,
                               updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_outbox_publish_ready
    ON outbox_events(next_attempt_at, created_at)
    WHERE status IN ('PENDING', 'FAILED');

CREATE INDEX idx_outbox_status_created
    ON outbox_events(status, created_at DESC);

CREATE INDEX idx_outbox_aggregate
    ON outbox_events(aggregate_type, aggregate_id);

CREATE INDEX idx_outbox_event_type_created
    ON outbox_events(event_type, created_at DESC);

CREATE INDEX idx_outbox_topic_created
    ON outbox_events(topic, created_at DESC);