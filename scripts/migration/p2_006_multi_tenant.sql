-- P2-006 多租户隔离增量迁移（可重复执行）
BEGIN;

CREATE TABLE IF NOT EXISTS tenant (
    id             BIGSERIAL PRIMARY KEY,
    tenant_id      VARCHAR(64) NOT NULL UNIQUE,
    name           VARCHAR(128) NOT NULL,
    status         VARCHAR(16) NOT NULL DEFAULT 'ACTIVE', -- ACTIVE / SUSPENDED
    max_strategies INT NOT NULL DEFAULT 100,
    max_accounts   INT NOT NULL DEFAULT 100,
    max_daily_orders INT NOT NULL DEFAULT 10000,
    remark         VARCHAR(256),
    create_time    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted        SMALLINT DEFAULT 0
);

-- 策略数据按租户隔离（存量归 DEFAULT）
ALTER TABLE strategy_info ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) NOT NULL DEFAULT 'DEFAULT';
CREATE INDEX IF NOT EXISTS idx_strategy_tenant ON strategy_info(tenant_id, deleted);

INSERT INTO tenant (tenant_id, name, status)
SELECT 'DEFAULT','默认租户','ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM tenant WHERE tenant_id='DEFAULT');

COMMIT;