CREATE TABLE notifications (
    id          UUID            PRIMARY KEY,
    recipient   VARCHAR(160)    NOT NULL,
    channel     VARCHAR(16)     NOT NULL,
    message     VARCHAR(500)    NOT NULL,
    sent_at     TIMESTAMPTZ     NOT NULL
);
