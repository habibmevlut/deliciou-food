-- Database per Service: payments şeması yalnızca payment-service'e aittir.
-- order_id bir UUID referanstır; FK constraint başka servisin DB'sine gidemez.

CREATE TABLE payments (
    id          UUID            PRIMARY KEY,
    order_id    UUID            NOT NULL,
    amount      NUMERIC(12, 2)  NOT NULL,
    status      VARCHAR(32)     NOT NULL,
    created_at  TIMESTAMPTZ     NOT NULL,
    updated_at  TIMESTAMPTZ     NOT NULL
);

CREATE INDEX idx_payments_order_id ON payments (order_id);
