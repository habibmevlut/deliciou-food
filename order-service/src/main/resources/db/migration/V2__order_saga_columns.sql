-- Saga ilerleme bilgisi order-service'in kendi tablosunda kalır.
-- payment / restaurant / delivery satırlarına FK yok (Database per Service).

ALTER TABLE orders
    ADD COLUMN cancel_reason VARCHAR(128),
    ADD COLUMN force_restaurant_reject BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN force_restaurant_accept BOOLEAN NOT NULL DEFAULT FALSE;
