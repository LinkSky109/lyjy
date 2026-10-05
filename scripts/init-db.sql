-- 初始化数据库表结构
-- 企业级量化交易平台 PostgreSQL 初始化脚本

-- 订单表
CREATE TABLE IF NOT EXISTS orders (
    id BIGSERIAL PRIMARY KEY,
    order_id VARCHAR(64) NOT NULL UNIQUE,
    strategy_id VARCHAR(64) NOT NULL,
    account_id VARCHAR(64) NOT NULL,
    symbol VARCHAR(32) NOT NULL,
    direction VARCHAR(8) NOT NULL,
    offset_flag VARCHAR(16) NOT NULL DEFAULT 'OPEN',
    order_type VARCHAR(16) NOT NULL,
    price DECIMAL(20,8) NOT NULL,
    quantity DECIMAL(20,8) NOT NULL,
    filled_quantity DECIMAL(20,8) DEFAULT 0,
    avg_fill_price DECIMAL(20,8) DEFAULT 0,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    exchange_order_id VARCHAR(64),
    error_code VARCHAR(64),
    error_message TEXT,
    submitted_at TIMESTAMP,
    filled_at TIMESTAMP,
    canceled_at TIMESTAMP,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted SMALLINT DEFAULT 0
);
CREATE INDEX idx_orders_strategy ON orders(strategy_id);
CREATE INDEX idx_orders_symbol ON orders(symbol);
CREATE INDEX idx_orders_status ON orders(status);

-- 资金账户表
CREATE TABLE IF NOT EXISTS fund_account (
    id BIGSERIAL PRIMARY KEY,
    account_id VARCHAR(64) NOT NULL UNIQUE,
    account_name VARCHAR(128),
    account_type VARCHAR(16) NOT NULL,
    currency VARCHAR(8) DEFAULT 'CNY',
    total_asset DECIMAL(20,8) DEFAULT 0,
    available_cash DECIMAL(20,8) DEFAULT 0,
    frozen_cash DECIMAL(20,8) DEFAULT 0,
    margin DECIMAL(20,8) DEFAULT 0,
    unrealized_pnl DECIMAL(20,8) DEFAULT 0,
    realized_pnl DECIMAL(20,8) DEFAULT 0,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted SMALLINT DEFAULT 0
);

-- 持仓表
CREATE TABLE IF NOT EXISTS position (
    id BIGSERIAL PRIMARY KEY,
    account_id VARCHAR(64) NOT NULL,
    strategy_id VARCHAR(64) NOT NULL,
    symbol VARCHAR(32) NOT NULL,
    direction VARCHAR(8) NOT NULL,
    quantity DECIMAL(20,8) NOT NULL DEFAULT 0,
    available_quantity DECIMAL(20,8) NOT NULL DEFAULT 0,
    avg_cost DECIMAL(20,8) DEFAULT 0,
    unrealized_pnl DECIMAL(20,8) DEFAULT 0,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted SMALLINT DEFAULT 0,
    UNIQUE(account_id, strategy_id, symbol, direction)
);

-- 策略表
CREATE TABLE IF NOT EXISTS strategy (
    id BIGSERIAL PRIMARY KEY,
    strategy_id VARCHAR(64) NOT NULL UNIQUE,
    strategy_name VARCHAR(128),
    status VARCHAR(16) DEFAULT 'STOPPED',
    account_id VARCHAR(64),
    parameters JSONB,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 订单幂等记录表（防止重复下单）
CREATE TABLE IF NOT EXISTS order_idempotent (
    id BIGSERIAL PRIMARY KEY,
    idempotent_key VARCHAR(128) NOT NULL UNIQUE,
    order_id VARCHAR(64),
    request_hash VARCHAR(64) NOT NULL,
    status VARCHAR(16) DEFAULT 'PROCESSING',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_idempotent_key ON order_idempotent(idempotent_key);

-- 风控规则配置表
CREATE TABLE IF NOT EXISTS risk_rule_config (
    id BIGSERIAL PRIMARY KEY,
    rule_code VARCHAR(64) NOT NULL UNIQUE,
    rule_name VARCHAR(128),
    rule_type VARCHAR(32),
    enabled BOOLEAN DEFAULT true,
    threshold_params TEXT,
    action VARCHAR(16) DEFAULT 'REJECT',
    account_id VARCHAR(64),
    symbol VARCHAR(64),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 风控拦截日志表
CREATE TABLE IF NOT EXISTS risk_intercept_log (
    id BIGSERIAL PRIMARY KEY,
    order_id VARCHAR(64),
    account_id VARCHAR(64),
    strategy_id VARCHAR(64),
    symbol VARCHAR(64),
    rule_code VARCHAR(64),
    rule_name VARCHAR(128),
    reason TEXT,
    direction VARCHAR(16),
    price DECIMAL(20,8),
    quantity DECIMAL(20,8),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_risk_intercept_time ON risk_intercept_log(create_time);

-- 出入金申请表
CREATE TABLE IF NOT EXISTS cash_transaction (
    id BIGSERIAL PRIMARY KEY,
    transaction_no VARCHAR(64) NOT NULL UNIQUE,
    account_id VARCHAR(64) NOT NULL,
    type VARCHAR(16) NOT NULL,
    amount DECIMAL(20,4) NOT NULL,
    currency VARCHAR(8) DEFAULT 'CNY',
    status VARCHAR(16) DEFAULT 'PENDING',
    bank_account VARCHAR(128),
    applicant VARCHAR(64),
    approver VARCHAR(64),
    remark TEXT,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    approve_time TIMESTAMP,
    complete_time TIMESTAMP
);

-- 资金流水表
CREATE TABLE IF NOT EXISTS fund_flow (
    id BIGSERIAL PRIMARY KEY,
    flow_no VARCHAR(64) NOT NULL UNIQUE,
    account_id VARCHAR(64) NOT NULL,
    biz_type VARCHAR(32) NOT NULL,
    biz_no VARCHAR(64),
    change_amount DECIMAL(20,4) NOT NULL,
    balance_before DECIMAL(20,4),
    balance_after DECIMAL(20,4),
    symbol VARCHAR(64),
    remark TEXT,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_fund_flow_account_time ON fund_flow(account_id, create_time);

-- 资金对账记录表
CREATE TABLE IF NOT EXISTS fund_reconciliation (
    id BIGSERIAL PRIMARY KEY,
    recon_no VARCHAR(64) NOT NULL UNIQUE,
    account_id VARCHAR(64) NOT NULL,
    recon_date VARCHAR(16),
    system_balance DECIMAL(20,4),
    gateway_balance DECIMAL(20,4),
    system_available DECIMAL(20,4),
    gateway_available DECIMAL(20,4),
    system_margin DECIMAL(20,4),
    gateway_margin DECIMAL(20,4),
    difference DECIMAL(20,4),
    status VARCHAR(16),
    difference_reason TEXT,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 成交记录表
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
CREATE INDEX idx_trade_order ON trade_record(order_id);
CREATE INDEX idx_trade_account_time ON trade_record(account_id, trade_time);

-- 订单资金冻结记录
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
CREATE INDEX idx_reservation_account_status
    ON order_fund_reservation(account_id, status);

-- 网关回报处理日志
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
CREATE INDEX idx_gateway_report_status
    ON gateway_report_log(process_status, create_time);

-- 初始化默认账户
INSERT INTO fund_account (account_id, account_name, account_type, total_asset, available_cash)
VALUES ('DEFAULT-001', '默认测试账户', 'OWN', 1000000, 1000000)
ON CONFLICT (account_id) DO NOTHING;
