-- P6.1 账户间资金划转
-- 适用基线：P0/P1/P5。只新增表，不改动现有资金表。

BEGIN;

CREATE TABLE IF NOT EXISTS fund_transfer (
    id BIGSERIAL PRIMARY KEY,
    transfer_no VARCHAR(64) NOT NULL UNIQUE,
    from_account_id VARCHAR(64) NOT NULL,
    to_account_id VARCHAR(64) NOT NULL,
    amount DECIMAL(20, 8) NOT NULL,
    currency VARCHAR(8) NOT NULL DEFAULT 'CNY',
    status VARCHAR(16) NOT NULL,
    idempotency_key VARCHAR(128),
    operator VARCHAR(64),
    remark VARCHAR(255),
    from_balance_before DECIMAL(20, 8),
    from_balance_after DECIMAL(20, 8),
    to_balance_before DECIMAL(20, 8),
    to_balance_after DECIMAL(20, 8),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    complete_time TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_fund_transfer_idempotency
    ON fund_transfer(idempotency_key)
    WHERE idempotency_key IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_fund_transfer_from
    ON fund_transfer(from_account_id, create_time DESC);

CREATE INDEX IF NOT EXISTS idx_fund_transfer_to
    ON fund_transfer(to_account_id, create_time DESC);

COMMIT;
