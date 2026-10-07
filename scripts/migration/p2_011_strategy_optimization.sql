-- P2-011 策略优化存档表（OBS-012，幂等）
BEGIN;

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

COMMIT;