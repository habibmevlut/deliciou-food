-- Idempotent consumer (yalnızca payment-service).
-- Outbox en az bir kez teslim eder: aynı commandId ikinci kez gelirse iş + event tekrarlanmaz.
-- event_id aslında Kafka komutunun commandId'sidir; tablo adı brief'teki processed_events.

CREATE TABLE processed_events (
    event_id      VARCHAR(64)     PRIMARY KEY,
    event_type    VARCHAR(128)    NOT NULL,
    order_id      UUID,
    processed_at  TIMESTAMPTZ     NOT NULL
);

CREATE INDEX idx_processed_events_order_id ON processed_events (order_id);
