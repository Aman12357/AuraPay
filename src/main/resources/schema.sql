CREATE TABLE IF NOT EXISTS demo_orders (
    order_id VARCHAR(36) PRIMARY KEY,
    pack_id VARCHAR(80) NOT NULL,
    pack_name VARCHAR(120) NOT NULL,
    credits INTEGER NOT NULL,
    amount_in_paise BIGINT NOT NULL,
    status VARCHAR(40) NOT NULL,
    real_payments_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    created_at VARCHAR(50) NOT NULL
);
