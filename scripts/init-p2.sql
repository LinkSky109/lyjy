-- 初始化 P2 新增表结构（增量，可重复执行）
-- 企业级量化交易平台 P2 三期（监管报送与 AML 先行）
-- 与 init-p1.sql 风格一致：BIGSERIAL 主键 / DECIMAL(20,8) / TIMESTAMP / SMALLINT deleted

-- ========== 1. 监管报送批次 ==========
CREATE TABLE IF NOT EXISTS regulatory_report_batch (
    id            BIGSERIAL PRIMARY KEY,
    batch_id      VARCHAR(64) NOT NULL UNIQUE,
    report_type   VARCHAR(32) NOT NULL,             -- LARGE_TRADE / DAILY_TRADE
    report_date   DATE NOT NULL,
    status        VARCHAR(16) NOT NULL DEFAULT 'GENERATING', -- GENERATING/READY/SUBMITTED/FAILED
    total_items   INT NOT NULL DEFAULT 0,
    total_amount  DECIMAL(20,8) DEFAULT 0,
    file_name     VARCHAR(256),
    error_message VARCHAR(512),
    submit_by     VARCHAR(64),
    submit_time   TIMESTAMP,
    create_time   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted       SMALLINT DEFAULT 0,
    UNIQUE(report_type, report_date)
);
CREATE INDEX IF NOT EXISTS idx_reg_batch_date ON regulatory_report_batch(report_type, report_date);

-- ========== 2. 监管报送明细 ==========
CREATE TABLE IF NOT EXISTS regulatory_report_item (
    id           BIGSERIAL PRIMARY KEY,
    batch_id     VARCHAR(64) NOT NULL,
    row_no       INT NOT NULL,
    content_json TEXT NOT NULL DEFAULT '{}',
    status       VARCHAR(16) NOT NULL DEFAULT 'READY',  -- READY / EXCLUDED
    create_time  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_reg_item_batch ON regulatory_report_item(batch_id);

-- ========== 3. AML 阈值规则 ==========
CREATE TABLE IF NOT EXISTS aml_rule (
    id             BIGSERIAL PRIMARY KEY,
    rule_id        VARCHAR(64) NOT NULL UNIQUE,
    rule_code      VARCHAR(32) NOT NULL,             -- LARGE_SINGLE / LARGE_CUMULATIVE
    name           VARCHAR(128) NOT NULL,
    threshold      DECIMAL(20,8) NOT NULL,           -- 金额(CNY) 或次数
    period_seconds INT DEFAULT 86400,
    severity       VARCHAR(8) NOT NULL DEFAULT 'WARN',
    enabled        BOOLEAN NOT NULL DEFAULT TRUE,
    create_time    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted        SMALLINT DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_aml_rule_enabled ON aml_rule(enabled) WHERE enabled AND deleted=0;

-- ========== 4. AML 告警 ==========
CREATE TABLE IF NOT EXISTS aml_alert (
    id           BIGSERIAL PRIMARY KEY,
    alert_id     VARCHAR(64) NOT NULL UNIQUE,
    rule_code    VARCHAR(32) NOT NULL,               -- LARGE_SINGLE / LARGE_CUMULATIVE
    mode         VARCHAR(16) NOT NULL,
    account_id   VARCHAR(64),
    strategy_id  VARCHAR(64),
    symbol       VARCHAR(32),
    amount       DECIMAL(20,8) NOT NULL,
    threshold    DECIMAL(20,8) NOT NULL,
    evidence     TEXT,
    severity     VARCHAR(8) NOT NULL DEFAULT 'WARN',
    status       VARCHAR(16) NOT NULL DEFAULT 'OPEN', -- OPEN/REPORTED/CLOSED
    handler      VARCHAR(64),
    handle_note  VARCHAR(512),
    create_time  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted      SMALLINT DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_aml_alert_status ON aml_alert(status, create_time DESC);

-- ========== 默认 AML 规则 ==========
INSERT INTO aml_rule (rule_id, rule_code, name, threshold, period_seconds, severity, enabled)
VALUES
    ('AML-LARGE-SINGLE', 'LARGE_SINGLE', '单笔大额交易', 500000, 0, 'WARN', TRUE),
    ('AML-LARGE-CUMULATIVE', 'LARGE_CUMULATIVE', '账户累计大额交易', 1000000, 86400, 'WARN', TRUE)
ON CONFLICT (rule_id) DO NOTHING;

-- ========== 5. 策略水印与运行环境绑定 ==========
CREATE TABLE IF NOT EXISTS strategy_watermark (
    id             BIGSERIAL PRIMARY KEY,
    strategy_id    VARCHAR(64) NOT NULL,
    version        INT NOT NULL DEFAULT 1,
    token          VARCHAR(64) NOT NULL UNIQUE,
    owner          VARCHAR(64) NOT NULL,
    uploaded_by    VARCHAR(64),
    source_hash    VARCHAR(64),
    env_fingerprint VARCHAR(128),
    create_time    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted        SMALLINT DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_wm_strategy ON strategy_watermark(strategy_id, version);

-- ========== 6. 风险预算分配与占用监控 ==========
CREATE TABLE IF NOT EXISTS risk_budget (
    id            BIGSERIAL PRIMARY KEY,
    budget_id     VARCHAR(64) NOT NULL UNIQUE,
    scope_type    VARCHAR(16) NOT NULL,
    scope_id      VARCHAR(64) NOT NULL,
    budget_type   VARCHAR(16) NOT NULL DEFAULT 'VAR',
    limit_value   DECIMAL(20,8) NOT NULL,
    current_value DECIMAL(20,8) DEFAULT 0,
    usage_rate    DECIMAL(10,6) DEFAULT 0,
    status        VARCHAR(16) NOT NULL DEFAULT 'OK',
    enabled       BOOLEAN NOT NULL DEFAULT TRUE,
    remark        VARCHAR(256),
    updated_by    VARCHAR(64),
    create_time   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted       SMALLINT DEFAULT 0,
    UNIQUE(scope_type, scope_id, budget_type)
);
CREATE INDEX IF NOT EXISTS idx_risk_budget_scope ON risk_budget(scope_type, scope_id);

-- ========== 7. 绩效归因报告 ==========
CREATE TABLE IF NOT EXISTS attribution_report (
    id             BIGSERIAL PRIMARY KEY,
    report_id      VARCHAR(64) NOT NULL UNIQUE,
    attr_type      VARCHAR(16) NOT NULL,
    scope_type     VARCHAR(16),
    scope_id       VARCHAR(64),
    period_start   DATE,
    period_end     DATE,
    result_json    TEXT NOT NULL DEFAULT '{}',
    operator       VARCHAR(64),
    create_time    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted        SMALLINT DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_attr_scope ON attribution_report(attr_type, scope_id, create_time DESC);

-- ========== 8. 跨境交收与多币种 ==========
CREATE TABLE IF NOT EXISTS settlement_rule (
    id             BIGSERIAL PRIMARY KEY,
    rule_id        VARCHAR(64) NOT NULL UNIQUE,
    market         VARCHAR(16) NOT NULL,
    exchange_match VARCHAR(32),
    cycle_days     INT NOT NULL DEFAULT 0,
    currency       VARCHAR(8) NOT NULL,
    remark         VARCHAR(256),
    enabled        BOOLEAN NOT NULL DEFAULT TRUE,
    create_time    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted        SMALLINT DEFAULT 0
);
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
    status        VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    settled_by    VARCHAR(64),
    settled_at    TIMESTAMP,
    remark        VARCHAR(512),
    create_time   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted       SMALLINT DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_settle_task_status ON settlement_task(status, expected_date);
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

-- ========== 9. 多租户隔离 ==========
CREATE TABLE IF NOT EXISTS tenant (
    id             BIGSERIAL PRIMARY KEY,
    tenant_id      VARCHAR(64) NOT NULL UNIQUE,
    name           VARCHAR(128) NOT NULL,
    status         VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    max_strategies INT NOT NULL DEFAULT 100,
    max_accounts   INT NOT NULL DEFAULT 100,
    max_daily_orders INT NOT NULL DEFAULT 10000,
    remark         VARCHAR(256),
    create_time    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted        SMALLINT DEFAULT 0
);
ALTER TABLE strategy_info ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) NOT NULL DEFAULT 'DEFAULT';
CREATE INDEX IF NOT EXISTS idx_strategy_tenant ON strategy_info(tenant_id, deleted);
INSERT INTO tenant (tenant_id, name, status)
SELECT 'DEFAULT','默认租户','ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM tenant WHERE tenant_id='DEFAULT');

-- ========== 10. 容量管理与自动伸缩 ==========
CREATE TABLE IF NOT EXISTS capacity_snapshot (
    id               BIGSERIAL PRIMARY KEY,
    sample_time      TIMESTAMP NOT NULL,
    cpu_pct          DECIMAL(6,2),
    mem_pct          DECIMAL(6,2),
    disk_pct         DECIMAL(6,2),
    active_strategies INT NOT NULL DEFAULT 0,
    queue_depth      INT NOT NULL DEFAULT 0,
    inbound_tps      INT NOT NULL DEFAULT 0,
    load_pct         DECIMAL(6,2) NOT NULL,
    status           VARCHAR(16) NOT NULL DEFAULT 'NORMAL',
    desired_replicas INT NOT NULL DEFAULT 1,
    detail           TEXT NOT NULL DEFAULT '{}'
);
CREATE INDEX IF NOT EXISTS idx_capacity_time ON capacity_snapshot(sample_time DESC);

-- ========== 11. 多租户横切扩展：组合隔离与组合配额 ==========
ALTER TABLE portfolio ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) NOT NULL DEFAULT 'DEFAULT';
CREATE INDEX IF NOT EXISTS idx_portfolio_tenant ON portfolio(tenant_id, deleted);
ALTER TABLE tenant ADD COLUMN IF NOT EXISTS max_portfolios INT NOT NULL DEFAULT 100;

-- ========== 12. 多租户横切扩展：风险预算按租户隔离 ==========
ALTER TABLE risk_budget ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) NOT NULL DEFAULT 'DEFAULT';
CREATE INDEX IF NOT EXISTS idx_risk_budget_tenant ON risk_budget(tenant_id, deleted);

-- ========== 13. 策略优化存档 ==========
CREATE TABLE IF NOT EXISTS strategy_optimization (
    id               BIGSERIAL PRIMARY KEY,
    batch_id         VARCHAR(64) NOT NULL,
    symbol           VARCHAR(32) NOT NULL,
    frequency        VARCHAR(8)  NOT NULL DEFAULT '1h',
    mode             VARCHAR(8)  NOT NULL DEFAULT 'close',
    objective        VARCHAR(16) NOT NULL DEFAULT 'SHARPE',
    best_fast        INT NOT NULL,
    best_slow        INT NOT NULL,
    best_return      DECIMAL(20,8),
    best_sharpe      DECIMAL(20,8),
    best_drawdown    DECIMAL(20,8),
    baseline_return  DECIMAL(20,8),
    bars_used        INT NOT NULL DEFAULT 0,
    start_time       VARCHAR(32),
    end_time         VARCHAR(32),
    runs_json        TEXT NOT NULL DEFAULT '[]',
    create_time      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_opt_batch ON strategy_optimization(batch_id);
CREATE INDEX IF NOT EXISTS idx_opt_symbol ON strategy_optimization(symbol, create_time DESC);

-- ========== 14. 策略回撤熔断 ==========
CREATE TABLE IF NOT EXISTS strategy_circuit (
    id               BIGSERIAL PRIMARY KEY,
    strategy_id      VARCHAR(64) NOT NULL UNIQUE,
    max_drawdown_pct DECIMAL(10,6) NOT NULL DEFAULT 0.05,
    enabled          BOOLEAN NOT NULL DEFAULT true,
    peak_pnl_rate    DECIMAL(20,8),
    tripped          BOOLEAN NOT NULL DEFAULT false,
    last_trip_time   TIMESTAMP,
    update_time      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted          SMALLINT DEFAULT 0
);

-- ========== 15. 组合级风控（多策略） ==========
CREATE TABLE IF NOT EXISTS portfolio_circuit (
    id               BIGSERIAL PRIMARY KEY,
    portfolio_key    VARCHAR(64) NOT NULL UNIQUE,
    member_strategies TEXT NOT NULL DEFAULT '[]',
    max_drawdown_pct DECIMAL(10,6) NOT NULL DEFAULT 0.05,
    enabled          BOOLEAN NOT NULL DEFAULT true,
    peak_pnl_rate    DECIMAL(20,8),
    tripped          BOOLEAN NOT NULL DEFAULT false,
    last_trip_time   TIMESTAMP,
    update_time      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted          SMALLINT DEFAULT 0
);

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