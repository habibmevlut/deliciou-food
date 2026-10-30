CREATE TABLE deliveries (
    id            UUID           PRIMARY KEY,
    order_id      UUID           NOT NULL,
    courier_name  VARCHAR(120)   NOT NULL,
    status        VARCHAR(32)    NOT NULL,
    created_at    TIMESTAMPTZ    NOT NULL,
    updated_at    TIMESTAMPTZ    NOT NULL
);
CREATE INDEX idx_deliveries_order_id ON deliveries (order_id);
