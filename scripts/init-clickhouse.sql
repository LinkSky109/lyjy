-- ClickHouse 行情数据库初始化脚本
-- 执行方式：clickhouse-client --multiquery < init-clickhouse.sql

CREATE DATABASE IF NOT EXISTS lhjypt;

-- Tick行情表（Level-2五档）
CREATE TABLE IF NOT EXISTS lhjypt.tick_data
(
    symbol          String,
    last_price      Decimal64(8),
    pre_close       Decimal64(8),
    open            Decimal64(8),
    high            Decimal64(8),
    low             Decimal64(8),
    volume          UInt64,
    turnover        Decimal64(8),
    open_interest   UInt64,
    bid_price1      Decimal64(8),
    bid_price2      Decimal64(8),
    bid_price3      Decimal64(8),
    bid_price4      Decimal64(8),
    bid_price5      Decimal64(8),
    bid_volume1     UInt64,
    bid_volume2     UInt64,
    bid_volume3     UInt64,
    bid_volume4     UInt64,
    bid_volume5     UInt64,
    ask_price1      Decimal64(8),
    ask_price2      Decimal64(8),
    ask_price3      Decimal64(8),
    ask_price4      Decimal64(8),
    ask_price5      Decimal64(8),
    ask_volume1     UInt64,
    ask_volume2     UInt64,
    ask_volume3     UInt64,
    ask_volume4     UInt64,
    ask_volume5     UInt64,
    tick_time       DateTime,
    recv_time       DateTime
)
ENGINE = MergeTree()
PARTITION BY toYYYYMMDD(tick_time)
ORDER BY (symbol, tick_time)
TTL tick_time + INTERVAL 365 DAY
SETTINGS index_granularity = 8192;

-- K线表（多周期）
CREATE TABLE IF NOT EXISTS lhjypt.bar_data
(
    symbol          String,
    frequency       String,     -- 1m/5m/15m/1h/1d
    open            Decimal64(8),
    high            Decimal64(8),
    low             Decimal64(8),
    close           Decimal64(8),
    volume          UInt64,
    turnover        Decimal64(8),
    open_interest   UInt64,
    bar_time        DateTime
)
ENGINE = MergeTree()
PARTITION BY (frequency, toYYYYMM(bar_time))
ORDER BY (symbol, frequency, bar_time)
TTL bar_time + INTERVAL 1095 DAY
SETTINGS index_granularity = 8192;

-- 行情订阅记录表
CREATE TABLE IF NOT EXISTS lhjypt.market_subscription
(
    symbol          String,
    subscribed_at   DateTime,
    source          String
)
ENGINE = MergeTree()
ORDER BY (symbol, subscribed_at);

-- ==================== P1 监控指标采样表 ====================
CREATE TABLE IF NOT EXISTS lhjypt.metric_sample
(
    ts              DateTime('Asia/Shanghai'),
    metric          String,
    value           Float64,
    labels          String     -- JSON: {"gateway":"xxx","strategy":"xxx"}
)
ENGINE = MergeTree()
PARTITION BY toYYYYMMDD(ts)
ORDER BY (ts, metric)
TTL ts + INTERVAL 90 DAY
SETTINGS index_granularity = 8192;
