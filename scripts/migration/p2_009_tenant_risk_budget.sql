-- P2-009 多租户横切扩展：风险预算按租户隔离增量迁移（可重复执行）
BEGIN;

ALTER TABLE risk_budget ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) NOT NULL DEFAULT 'DEFAULT';
CREATE INDEX IF NOT EXISTS idx_risk_budget_tenant ON risk_budget(tenant_id, deleted);

COMMIT;