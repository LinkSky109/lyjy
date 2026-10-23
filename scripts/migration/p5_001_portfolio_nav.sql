-- P5-001 组合净值快照与持仓策略归属
CREATE TABLE IF NOT EXISTS portfolio_nav (
    id BIGSERIAL PRIMARY KEY,
    portfolio_id VARCHAR(64) NOT NULL,
    nav_date DATE NOT NULL,
    nav DECIMAL(20, 8) NOT NULL,
    equity DECIMAL(20, 8) NOT NULL,
    cash DECIMAL(20, 8),
    source VARCHAR(32) DEFAULT 'SNAPSHOT',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (portfolio_id, nav_date)
);

CREATE INDEX IF NOT EXISTS idx_portfolio_nav_date
    ON portfolio_nav (portfolio_id, nav_date);

ALTER TABLE portfolio_position
    ADD COLUMN IF NOT EXISTS strategy_id VARCHAR(64);
