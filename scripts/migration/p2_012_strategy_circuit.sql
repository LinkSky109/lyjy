-- P2-012 策略回撤熔断（OBS-016，幂等）
BEGIN;

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

COMMIT;