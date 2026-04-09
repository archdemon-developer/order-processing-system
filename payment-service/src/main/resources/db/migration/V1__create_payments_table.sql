CREATE TABLE payments (
    id            UUID        PRIMARY KEY,
    customer_id   UUID        NOT NULL,
    order_id   UUID        NOT NULL,
    transaction_id   UUID,
    status        VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    attempts      INT       NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);