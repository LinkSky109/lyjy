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