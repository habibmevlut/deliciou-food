CREATE TABLE customers (
    id          UUID            PRIMARY KEY,
    full_name   VARCHAR(160)    NOT NULL,
    email       VARCHAR(160)    NOT NULL UNIQUE,
    phone       VARCHAR(32)     NOT NULL,
    created_at  TIMESTAMPTZ     NOT NULL,
    updated_at  TIMESTAMPTZ     NOT NULL
);

CREATE TABLE addresses (
    id          UUID            PRIMARY KEY,
    customer_id UUID            NOT NULL REFERENCES customers (id) ON DELETE CASCADE,
    title       VARCHAR(80)     NOT NULL,
    street      VARCHAR(255)    NOT NULL,
    city        VARCHAR(80)     NOT NULL,
    created_at  TIMESTAMPTZ     NOT NULL,
    updated_at  TIMESTAMPTZ     NOT NULL
);

CREATE INDEX idx_addresses_customer_id ON addresses (customer_id);
