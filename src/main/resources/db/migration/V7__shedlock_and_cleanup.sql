-- ShedLock internal table for distributed locking.
CREATE TABLE shedlock (
    name        VARCHAR(64) NOT NULL,
    lock_until  TIMESTAMP NOT NULL,
    locked_at   TIMESTAMP NOT NULL,
    locked_by   VARCHAR(255) NOT NULL,
    PRIMARY KEY (name)
);

CREATE INDEX idx_payment_orders_status_created
    ON payment_orders(status)
    WHERE status = 'CREATED';
