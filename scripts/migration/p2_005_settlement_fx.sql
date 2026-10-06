-- P2-005 跨境交收与多币种增量迁移（可重复执行）
BEGIN;

-- 1. 交收规则（T+N + 币种）
CREATE TABLE IF NOT EXISTS settlement_rule (
    id             BIGSERIAL PRIMARY KEY,
    rule_id        VARCHAR(64) NOT NULL UNIQUE,
    market         VARCHAR(16) NOT NULL,             -- CN / HK / US
    exchange_match VARCHAR(32),                      -- 空=该市场全部
    cycle_days     INT NOT NULL DEFAULT 0,           -- T+N
    currency       VARCHAR(8) NOT NULL,
    remark         VARCHAR(256),
    enabled        BOOLEAN NOT NULL DEFAULT TRUE,
    create_time    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted        SMALLINT DEFAULT 0
);

-- 2. 资金交收任务状态机
CREATE TABLE IF NOT EXISTS settlement_task (
    id            BIGSERIAL PRIMARY KEY,
    task_id       VARCHAR(64) NOT NULL UNIQUE,
    trade_id      VARCHAR(64) NOT NULL,
    account_id    VARCHAR(64),
    symbol        VARCHAR(32),
    exchange      VARCHAR(16),
    currency      VARCHAR(8),
    cycle_days    INT NOT NULL DEFAULT 0,
    trade_date    DATE,
    expected_date DATE NOT NULL,
    amount        DECIMAL(20,8) DEFAULT 0,
    status        VARCHAR(16) NOT NULL DEFAULT 'PENDING', -- PENDING/SETTLING/SETTLED/FAILED
    settled_by    VARCHAR(64),
    settled_at    TIMESTAMP,
    remark        VARCHAR(512),
    create_time   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted       SMALLINT DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_settle_task_status ON settlement_task(status, expected_date);

-- 3. 汇率
CREATE TABLE IF NOT EXISTS currency_rate (
    id           BIGSERIAL PRIMARY KEY,
    base         VARCHAR(8) NOT NULL,
    quote        VARCHAR(8) NOT NULL,
    rate         DECIMAL(20,8) NOT NULL,
    rate_date    DATE NOT NULL,
    create_time  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(base, quote, rate_date)
);
CREATE INDEX IF NOT EXISTS idx_currency_pair ON currency_rate(base, quote, rate_date DESC);

-- 默认交收规则与主要汇率
INSERT INTO settlement_rule (rule_id, market, exchange_match, cycle_days, currency, remark)
VALUES
    ('R-CN-FUT','CN',NULL,0,'CNY','境内期货 T+0'),
    ('R-CN-STK','CN','SSE',1,'CNY','A股沪市 T+1'),
    ('R-CN-STK-SZ','CN','SZSE',1,'CNY','A股深市 T+1'),
    ('R-HK','HK',NULL,2,'HKD','港股/港股通 T+2'),
    ('R-US','US',NULL,2,'USD','美股 T+2')
ON CONFLICT (rule_id) DO NOTHING;

INSERT INTO currency_rate (base, quote, rate, rate_date)
SELECT 'CNY','USD',0.14, CURRENT_DATE
WHERE NOT EXISTS (SELECT 1 FROM currency_rate WHERE base='CNY' AND quote='USD');
INSERT INTO currency_rate (base, quote, rate, rate_date)
SELECT 'CNY','HKD',1.08, CURRENT_DATE
WHERE NOT EXISTS (SELECT 1 FROM currency_rate WHERE base='CNY' AND quote='HKD');
INSERT INTO currency_rate (base, quote, rate, rate_date)
SELECT 'USD','HKD',7.80, CURRENT_DATE
WHERE NOT EXISTS (SELECT 1 FROM currency_rate WHERE base='USD' AND quote='HKD');

COMMIT;