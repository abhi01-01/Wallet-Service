-- ============================================================
-- V6: Webhook idempotency schema
-- ============================================================

CREATE TABLE webhook_events (
    id                  BIGSERIAL PRIMARY KEY,
    event_id            VARCHAR(255) NOT NULL,
    event_type          VARCHAR(100) NOT NULL,
    order_id            VARCHAR(255) NOT NULL,
    status              VARCHAR(20) NOT NULL DEFAULT 'RECEIVED'
                            CHECK (status IN ('RECEIVED', 'PROCESSING', 'PROCESSED', 'FAILED')),
    payload             JSONB NOT NULL,
    processing_attempts INTEGER NOT NULL DEFAULT 0 CHECK (processing_attempts >= 0),
    failure_reason      TEXT,
    received_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    processed_at        TIMESTAMPTZ,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_webhook_events_status_received
    ON webhook_events(status)
    WHERE status = 'RECEIVED';

CREATE INDEX idx_webhook_events_order_id
    ON webhook_events(order_id);
