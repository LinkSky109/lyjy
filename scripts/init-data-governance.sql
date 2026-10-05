-- 数据治理相关表结构

-- 复权因子表（除权除息事件）
CREATE TABLE IF NOT EXISTS adjustment_factor (
    id BIGSERIAL PRIMARY KEY,
    symbol VARCHAR(32) NOT NULL,
    ex_date DATE NOT NULL,
    event_type VARCHAR(16) NOT NULL,
    dividend_per_share DECIMAL(20,8) DEFAULT 0,
    split_ratio DECIMAL(20,8) DEFAULT 0,
    rights_price DECIMAL(20,8) DEFAULT 0,
    rights_ratio DECIMAL(20,8) DEFAULT 0,
    factor DECIMAL(20,8) NOT NULL,
    cumulative_factor DECIMAL(20,8),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(symbol, ex_date, event_type)
);
CREATE INDEX idx_adjustment_symbol ON adjustment_factor(symbol);
CREATE INDEX idx_adjustment_exdate ON adjustment_factor(ex_date);

-- 数据质量日志表
CREATE TABLE IF NOT EXISTS data_quality_log (
    id BIGSERIAL PRIMARY KEY,
    symbol VARCHAR(32) NOT NULL,
    error_type VARCHAR(32) NOT NULL,
    error_message TEXT,
    source VARCHAR(16),
    raw_data JSONB,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_dq_log_symbol ON data_quality_log(symbol);
CREATE INDEX idx_dq_log_time ON data_quality_log(create_time);
CREATE INDEX idx_dq_log_type ON data_quality_log(error_type);

-- 数据质量日统计表
CREATE TABLE IF NOT EXISTS data_quality_daily (
    id BIGSERIAL PRIMARY KEY,
    stat_date DATE NOT NULL,
    symbol VARCHAR(32) NOT NULL,
    total_received BIGINT DEFAULT 0,
    clean_count BIGINT DEFAULT 0,
    dirty_count BIGINT DEFAULT 0,
    missing_count BIGINT DEFAULT 0,
    price_jump_count BIGINT DEFAULT 0,
    zero_value_count BIGINT DEFAULT 0,
    dirty_rate DECIMAL(8,4) DEFAULT 0,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(stat_date, symbol)
);
