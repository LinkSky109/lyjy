-- P1.5 交易主链路闭环增量迁移
-- 适用基线：P0 init-db.sql + P1 init-p1.sql
-- 本脚本只做增量添加，不删除或重命名现有列。

BEGIN;

-- 1. 扩展订单表
ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS offset_flag VARCHAR(16) NOT NULL DEFAULT 'OPEN',
    ADD COLUMN IF NOT EXISTS error_code VARCHAR(64),
    ADD COLUMN IF NOT EXISTS submitted_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS filled_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS canceled_at TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_orders_account_status
    ON orders(account_id, status);

-- 2. 扩展幂等表
ALTER TABLE order_idempotent
    ADD COLUMN IF NOT EXISTS update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP;

-- 3. 持仓表补齐逻辑删除字段
ALTER TABLE position
    ADD COLUMN IF NOT EXISTS deleted SMALLINT DEFAULT 0;

-- 4. 成交记录表
CREATE TABLE IF NOT EXISTS trade_record (
    id BIGSERIAL PRIMARY KEY,
    trade_id VARCHAR(64) NOT NULL UNIQUE,
    order_id VARCHAR(64) NOT NULL,
    exchange_order_id VARCHAR(64),
    account_id VARCHAR(64) NOT NULL,
    strategy_id VARCHAR(64),
    symbol VARCHAR(32) NOT NULL,
    exchange VARCHAR(16),
    direction VARCHAR(8) NOT NULL,
    offset_flag VARCHAR(16) NOT NULL,
    price DECIMAL(20,8) NOT NULL,
    quantity DECIMAL(20,8) NOT NULL,
    turnover DECIMAL(20,8) NOT NULL,
    commission DECIMAL(20,8) DEFAULT 0,
    realized_pnl DECIMAL(20,8) DEFAULT 0,
    trade_time TIMESTAMP NOT NULL,
    trading_day VARCHAR(16),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_trade_order
    ON trade_record(order_id);

CREATE INDEX IF NOT EXISTS idx_trade_account_time
    ON trade_record(account_id, trade_time);

-- 5. 订单资金冻结记录
CREATE TABLE IF NOT EXISTS order_fund_reservation (
    id BIGSERIAL PRIMARY KEY,
    order_id VARCHAR(64) NOT NULL UNIQUE,
    account_id VARCHAR(64) NOT NULL,
    reserved_margin DECIMAL(20,8) NOT NULL DEFAULT 0,
    reserved_commission DECIMAL(20,8) NOT NULL DEFAULT 0,
    released_amount DECIMAL(20,8) NOT NULL DEFAULT 0,
    consumed_amount DECIMAL(20,8) NOT NULL DEFAULT 0,
    status VARCHAR(32) NOT NULL DEFAULT 'RESERVED',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_reservation_account_status
    ON order_fund_reservation(account_id, status);

-- 6. 网关回报幂等日志
CREATE TABLE IF NOT EXISTS gateway_report_log (
    id BIGSERIAL PRIMARY KEY,
    gateway_id VARCHAR(64) NOT NULL,
    report_type VARCHAR(16) NOT NULL,
    report_key VARCHAR(128) NOT NULL,
    payload_hash VARCHAR(64),
    process_status VARCHAR(16) NOT NULL DEFAULT 'RECEIVED',
    retry_count INT NOT NULL DEFAULT 0,
    error_message TEXT,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(gateway_id, report_type, report_key)
);

CREATE INDEX IF NOT EXISTS idx_gateway_report_status
    ON gateway_report_log(process_status, create_time);

COMMIT;
