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

CREATE TABLE IF NOT EXISTS payment_qr (
    id INTEGER PRIMARY KEY,
    image_data BYTEA NOT NULL,
    content_type VARCHAR(50) NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

-- UPI claims are a reconciliation queue, not payment evidence.
-- A unique reference prevents duplicate claims but never proves funds were received.
CREATE TABLE IF NOT EXISTS upi_payment_claims (
    claim_id VARCHAR(36) PRIMARY KEY,
    order_id VARCHAR(36) NOT NULL REFERENCES demo_orders(order_id),
    amount_in_paise BIGINT NOT NULL CHECK (amount_in_paise > 0),
    upi_reference VARCHAR(64) NOT NULL UNIQUE,
    status VARCHAR(40) NOT NULL CHECK (status IN ('PENDING_REVIEW', 'VERIFIED_MANUALLY', 'REJECTED')),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    reviewed_at TIMESTAMP WITH TIME ZONE,
    review_note VARCHAR(500)
);
