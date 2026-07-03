CREATE TABLE kafka_event_audit (
                                   id                  BIGSERIAL PRIMARY KEY,

                                   event_id            UUID NOT NULL UNIQUE,
                                   event_type          VARCHAR(120) NOT NULL,
                                   schema_version      INTEGER NOT NULL,
                                   source              VARCHAR(120) NOT NULL,

                                   aggregate_type      VARCHAR(80) NOT NULL,
                                   aggregate_id        VARCHAR(120) NOT NULL,

                                   topic               VARCHAR(150) NOT NULL,
                                   partition_id        INTEGER NOT NULL,
                                   event_offset        BIGINT NOT NULL,
                                   event_key           VARCHAR(150),

                                   payload             JSONB NOT NULL,
                                   headers             JSONB NOT NULL DEFAULT '{}'::jsonb,

                                   consumed_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),

                                   CONSTRAINT uk_kafka_event_audit_topic_partition_offset
                                       UNIQUE (topic, partition_id, event_offset)
);

CREATE INDEX idx_kafka_event_audit_event_type_consumed
    ON kafka_event_audit(event_type, consumed_at DESC);

CREATE INDEX idx_kafka_event_audit_aggregate
    ON kafka_event_audit(aggregate_type, aggregate_id);

CREATE INDEX idx_kafka_event_audit_topic_consumed
    ON kafka_event_audit(topic, consumed_at DESC);

CREATE INDEX idx_kafka_event_audit_consumed_at
    ON kafka_event_audit(consumed_at DESC);