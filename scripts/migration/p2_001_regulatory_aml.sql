-- P2-001 监管报送与 AML 增量迁移（可重复执行）
-- 适用基线：init-db.sql + init-p1.sql + init-p2.sql
BEGIN;

CREATE TABLE IF NOT EXISTS regulatory_report_batch (
    id            BIGSERIAL PRIMARY KEY,
    batch_id      VARCHAR(64) NOT NULL UNIQUE,
    report_type   VARCHAR(32) NOT NULL,
    report_date   DATE NOT NULL,
    status        VARCHAR(16) NOT NULL DEFAULT 'GENERATING',
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
CREATE INDEX IF NOT EXISTS idx_reg_batch_date
    ON regulatory_report_batch(report_type, report_date);

CREATE TABLE IF NOT EXISTS regulatory_report_item (
    id           BIGSERIAL PRIMARY KEY,
    batch_id     VARCHAR(64) NOT NULL,
    row_no       INT NOT NULL,
    content_json TEXT NOT NULL DEFAULT '{}',
    status       VARCHAR(16) NOT NULL DEFAULT 'READY',
    create_time  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_reg_item_batch ON regulatory_report_item(batch_id);

CREATE TABLE IF NOT EXISTS aml_rule (
    id             BIGSERIAL PRIMARY KEY,
    rule_id        VARCHAR(64) NOT NULL UNIQUE,
    rule_code      VARCHAR(32) NOT NULL,
    name           VARCHAR(128) NOT NULL,
    threshold      DECIMAL(20,8) NOT NULL,
    period_seconds INT DEFAULT 86400,
    severity       VARCHAR(8) NOT NULL DEFAULT 'WARN',
    enabled        BOOLEAN NOT NULL DEFAULT TRUE,
    create_time    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted        SMALLINT DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_aml_rule_enabled
    ON aml_rule(enabled) WHERE enabled AND deleted=0;

CREATE TABLE IF NOT EXISTS aml_alert (
    id           BIGSERIAL PRIMARY KEY,
    alert_id     VARCHAR(64) NOT NULL UNIQUE,
    rule_code    VARCHAR(32) NOT NULL,
    mode         VARCHAR(16) NOT NULL,
    account_id   VARCHAR(64),
    strategy_id  VARCHAR(64),
    symbol       VARCHAR(32),
    amount       DECIMAL(20,8) NOT NULL,
    threshold    DECIMAL(20,8) NOT NULL,
    evidence     TEXT,
    severity     VARCHAR(8) NOT NULL DEFAULT 'WARN',
    status       VARCHAR(16) NOT NULL DEFAULT 'OPEN',
    handler      VARCHAR(64),
    handle_note  VARCHAR(512),
    create_time  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted      SMALLINT DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_aml_alert_status
    ON aml_alert(status, create_time DESC);

INSERT INTO aml_rule (rule_id, rule_code, name, threshold, period_seconds, severity, enabled)
VALUES
    ('AML-LARGE-SINGLE', 'LARGE_SINGLE', '单笔大额交易', 500000, 0, 'WARN', TRUE),
    ('AML-LARGE-CUMULATIVE', 'LARGE_CUMULATIVE', '账户累计大额交易', 1000000, 86400, 'WARN', TRUE)
ON CONFLICT (rule_id) DO NOTHING;

COMMIT;