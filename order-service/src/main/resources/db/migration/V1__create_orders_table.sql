-- Database per Service: bu şema yalnızca order-service'e aittir.
-- payment / restaurant tablolarına buradan JOIN atılmaz.

CREATE TABLE orders (
    id              UUID            PRIMARY KEY,
    customer_id     UUID            NOT NULL,
    restaurant_id   UUID            NOT NULL,
    total_amount    NUMERIC(12, 2)  NOT NULL,
    status          VARCHAR(32)     NOT NULL,
    created_at      TIMESTAMPTZ     NOT NULL,
    updated_at      TIMESTAMPTZ     NOT NULL
);

CREATE INDEX idx_orders_customer_id ON orders (customer_id);
CREATE INDEX idx_orders_restaurant_id ON orders (restaurant_id);
