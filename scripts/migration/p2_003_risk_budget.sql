-- P2-003 风险预算分配与占用监控增量迁移（可重复执行）
BEGIN;

CREATE TABLE IF NOT EXISTS risk_budget (
    id            BIGSERIAL PRIMARY KEY,
    budget_id     VARCHAR(64) NOT NULL UNIQUE,
    scope_type    VARCHAR(16) NOT NULL,             -- ACCOUNT / PORTFOLIO / STRATEGY
    scope_id      VARCHAR(64) NOT NULL,
    budget_type   VARCHAR(16) NOT NULL DEFAULT 'VAR', -- VAR（对齐 VaR 口径）
    limit_value   DECIMAL(20,8) NOT NULL,
    current_value DECIMAL(20,8) DEFAULT 0,
    usage_rate    DECIMAL(10,6) DEFAULT 0,
    status        VARCHAR(16) NOT NULL DEFAULT 'OK', -- OK / WARN / BLOCK
    enabled       BOOLEAN NOT NULL DEFAULT TRUE,
    remark        VARCHAR(256),
    updated_by    VARCHAR(64),
    create_time   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted       SMALLINT DEFAULT 0,
    UNIQUE(scope_type, scope_id, budget_type)
);
CREATE INDEX IF NOT EXISTS idx_risk_budget_scope
    ON risk_budget(scope_type, scope_id);

COMMIT;