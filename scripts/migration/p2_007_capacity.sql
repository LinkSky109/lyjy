-- P2-007 容量管理与自动伸缩增量迁移（可重复执行）
BEGIN;

CREATE TABLE IF NOT EXISTS capacity_snapshot (
    id               BIGSERIAL PRIMARY KEY,
    sample_time      TIMESTAMP NOT NULL,
    cpu_pct          DECIMAL(6,2),
    mem_pct          DECIMAL(6,2),
    disk_pct         DECIMAL(6,2),
    active_strategies INT NOT NULL DEFAULT 0,
    queue_depth      INT NOT NULL DEFAULT 0,
    inbound_tps      INT NOT NULL DEFAULT 0,
    load_pct         DECIMAL(6,2) NOT NULL,          -- 综合水位
    status           VARCHAR(16) NOT NULL DEFAULT 'NORMAL', -- NORMAL/WARN/SCALE
    desired_replicas INT NOT NULL DEFAULT 1,
    detail           TEXT NOT NULL DEFAULT '{}'
);
CREATE INDEX IF NOT EXISTS idx_capacity_time ON capacity_snapshot(sample_time DESC);

COMMIT;