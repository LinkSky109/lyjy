-- P2-008 多租户横切扩展：组合（portfolio）数据隔离增量迁移（可重复执行）
BEGIN;

-- 组合数据按租户隔离（存量归 DEFAULT）
ALTER TABLE portfolio ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) NOT NULL DEFAULT 'DEFAULT';
CREATE INDEX IF NOT EXISTS idx_portfolio_tenant ON portfolio(tenant_id, deleted);

-- 租户配额新增组合上限
ALTER TABLE tenant ADD COLUMN IF NOT EXISTS max_portfolios INT NOT NULL DEFAULT 100;

COMMIT;