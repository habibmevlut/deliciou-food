-- Database per Service: restaurants şeması yalnızca restaurant-service'e aittir.

CREATE TABLE restaurants (
    id          UUID            PRIMARY KEY,
    name        VARCHAR(160)    NOT NULL,
    address     VARCHAR(255)    NOT NULL,
    cuisine     VARCHAR(80)     NOT NULL,
    is_open     BOOLEAN         NOT NULL,
    created_at  TIMESTAMPTZ     NOT NULL,
    updated_at  TIMESTAMPTZ     NOT NULL
);

CREATE INDEX idx_restaurants_name ON restaurants (name);
