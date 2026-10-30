-- Transactional Outbox (yalnızca order-service).
-- Orchestrator Kafka'ya doğrudan yazmaz: aynı Postgres transaction'ında buraya satır atar.
-- Polling publisher unpublished satırları Kafka'ya taşır, published_at doldurur.
-- payment / restaurant / delivery bu tabloyu görmez (Database per Service).

CREATE TABLE outbox_messages (
    id              UUID            PRIMARY KEY,
    topic           VARCHAR(128)    NOT NULL,
    message_key     VARCHAR(64)     NOT NULL,
    payload_type    VARCHAR(256)    NOT NULL,
    payload         TEXT            NOT NULL,
    created_at      TIMESTAMPTZ     NOT NULL,
    published_at    TIMESTAMPTZ,
    attempts        INTEGER         NOT NULL DEFAULT 0,
    last_error      VARCHAR(1024)
);

CREATE INDEX idx_outbox_unpublished
    ON outbox_messages (created_at)
    WHERE published_at IS NULL;
