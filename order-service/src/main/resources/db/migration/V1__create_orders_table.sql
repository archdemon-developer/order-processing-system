CREATE TABLE orders (
    id            UUID        PRIMARY KEY,
    customer_id   UUID        NOT NULL,
    status        VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    items         JSONB       NOT NULL,
    total_price   NUMERIC(19, 4) NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);