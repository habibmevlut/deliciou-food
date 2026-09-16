-- CQRS okuma modeli (yalnızca order-service).
-- orders = yazma modeli (şu anki status, tek satır).
-- order_history_view = sorgu modeli (olan biten, çok satır). JOIN yok; film şeridi.

CREATE TABLE order_history_view (
    id           UUID            PRIMARY KEY,
    order_id     UUID            NOT NULL,
    event_id     VARCHAR(64)     NOT NULL UNIQUE,
    step         VARCHAR(64)     NOT NULL,
    detail       VARCHAR(256),
    occurred_at  TIMESTAMPTZ     NOT NULL
);

CREATE INDEX idx_order_history_order_id ON order_history_view (order_id, occurred_at);
