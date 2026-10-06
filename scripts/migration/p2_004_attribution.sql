-- P2-004 绩效归因增量迁移（可重复执行）
BEGIN;

CREATE TABLE IF NOT EXISTS attribution_report (
    id             BIGSERIAL PRIMARY KEY,
    report_id      VARCHAR(64) NOT NULL UNIQUE,
    attr_type      VARCHAR(16) NOT NULL,             -- BRINSON / FACTOR / EXECUTION
    scope_type     VARCHAR(16),                      -- PORTFOLIO / STRATEGY / ACCOUNT
    scope_id       VARCHAR(64),
    period_start   DATE,
    period_end     DATE,
    result_json    TEXT NOT NULL DEFAULT '{}',
    operator       VARCHAR(64),
    create_time    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted        SMALLINT DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_attr_scope ON attribution_report(attr_type, scope_id, create_time DESC);

COMMIT;