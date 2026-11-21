CREATE TABLE IF NOT EXISTS orders (
    order_id TEXT PRIMARY KEY,
    amount_cents BIGINT NOT NULL CHECK (amount_cents > 0),
    status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING', 'PAID')),
    payment_applications INTEGER NOT NULL DEFAULT 0 CHECK (payment_applications >= 0),
    paid_at TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS processed_webhooks (
    event_id TEXT PRIMARY KEY,
    event_type TEXT NOT NULL CHECK (event_type = 'payment.approved'),
    order_id TEXT NOT NULL,
    amount_cents BIGINT NOT NULL CHECK (amount_cents > 0),
    processed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
