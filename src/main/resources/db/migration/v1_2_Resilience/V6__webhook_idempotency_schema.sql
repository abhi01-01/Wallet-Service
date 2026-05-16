-- ============================================================
-- V6: Webhook idempotency schema
-- ============================================================

CREATE TABLE webhook_events (
    id                  BIGSERIAL PRIMARY KEY,
    event_id            VARCHAR(255) NOT NULL,              -- Razorpay's x-razorpay-event-id
    event_type          VARCHAR(100) NOT NULL,              -- e.g., 'payment.captured'
    order_id            VARCHAR(255) NOT NULL,              -- Extracted for fast lookup (nullable if the event isn't order-specific)
    status              VARCHAR(20) NOT NULL DEFAULT 'RECEIVED'
                            CHECK (status IN ('RECEIVED', 'PROCESSING', 'PROCESSED', 'FAILED')),
    payload             JSONB NOT NULL,
    processing_attempts INTEGER NOT NULL DEFAULT 0 CHECK (processing_attempts >= 0),
    failure_reason      TEXT,
    received_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    processed_at        TIMESTAMPTZ,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Optimize for the polling query that looks for 'RECEIVED' rows
CREATE INDEX idx_webhook_events_status_received ON webhook_events(status) WHERE status = 'RECEIVED';
-- Optimize for looking up history by order
CREATE INDEX idx_webhook_events_order_id ON webhook_events(order_id);

