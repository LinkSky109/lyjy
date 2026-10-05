-- 初始化 P1 新增表结构
-- 企业级量化交易平台 P1 二期 PostgreSQL 增量脚本
-- 与 scripts/init-db.sql 风格一致：BIGSERIAL 主键 / DECIMAL(20,8) 金额 / TIMESTAMP / SMALLINT deleted
-- 约定：JSON 结构统一用 TEXT 列 + Java String 字段（对齐 P0 risk_rule_config.threshold_params 的成熟做法），不裸用 JSONB

-- ========== 1. 网关注册表 ==========
CREATE TABLE IF NOT EXISTS gateway_info (
    id             BIGSERIAL PRIMARY KEY,
    gateway_id     VARCHAR(64) NOT NULL UNIQUE,
    gateway_type   VARCHAR(32) NOT NULL,
    name           VARCHAR(128) NOT NULL,
    status         VARCHAR(16) NOT NULL DEFAULT 'DOWN',
    param_config   TEXT NOT NULL DEFAULT '{}',
    priority       INT NOT NULL DEFAULT 100,
    health_path    VARCHAR(128),
    last_health_at TIMESTAMP,
    description    VARCHAR(512),
    create_time    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted        SMALLINT DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_gateway_status ON gateway_info(status) WHERE deleted=0;

-- ========== 2. 通道路由规则表 ==========
CREATE TABLE IF NOT EXISTS gateway_route_rule (
    id           BIGSERIAL PRIMARY KEY,
    rule_id      VARCHAR(64) NOT NULL UNIQUE,
    rule_name    VARCHAR(128) NOT NULL,
    account_id   VARCHAR(64),
    symbol_regex VARCHAR(128),
    order_type   VARCHAR(16),
    gateway_id   VARCHAR(64) NOT NULL,
    fallback_id  VARCHAR(64),
    priority     INT NOT NULL DEFAULT 100,
    enabled      BOOLEAN NOT NULL DEFAULT TRUE,
    create_time  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted      SMALLINT DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_route_enabled ON gateway_route_rule(enabled) WHERE enabled AND deleted=0;

-- ========== 3. 策略注册表 ==========
CREATE TABLE IF NOT EXISTS strategy_info (
    id           BIGSERIAL PRIMARY KEY,
    strategy_id  VARCHAR(64) NOT NULL UNIQUE,
    name         VARCHAR(128) NOT NULL,
    owner        VARCHAR(64) NOT NULL,
    status       VARCHAR(16) NOT NULL DEFAULT 'OFFLINE',
    lang         VARCHAR(16) NOT NULL DEFAULT 'PYTHON',
    risk_profile VARCHAR(64),
    params_json  TEXT NOT NULL DEFAULT '{}',
    create_time  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted      SMALLINT DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_strategy_owner ON strategy_info(owner) WHERE deleted=0;

-- ========== 4. 组合定义表 ==========
CREATE TABLE IF NOT EXISTS portfolio (
    id            BIGSERIAL PRIMARY KEY,
    portfolio_id  VARCHAR(64) NOT NULL UNIQUE,
    name          VARCHAR(128) NOT NULL,
    owner         VARCHAR(64) NOT NULL,
    status        VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    base_currency VARCHAR(8) NOT NULL DEFAULT 'CNY',
    config_json   TEXT NOT NULL DEFAULT '{}',
    create_time   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted       SMALLINT DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_portfolio_owner ON portfolio(owner) WHERE deleted=0;

-- ========== 5. 组合持仓表 ==========
CREATE TABLE IF NOT EXISTS portfolio_position (
    id           BIGSERIAL PRIMARY KEY,
    portfolio_id VARCHAR(64) NOT NULL,
    symbol       VARCHAR(32) NOT NULL,
    exchange     VARCHAR(16),
    direction    VARCHAR(8) NOT NULL DEFAULT 'LONG',
    target_qty   DECIMAL(20,8) NOT NULL DEFAULT 0,
    cur_qty      DECIMAL(20,8) NOT NULL DEFAULT 0,
    weight_limit DECIMAL(8,6),
    update_time  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(portfolio_id, symbol, direction)
);
CREATE INDEX IF NOT EXISTS idx_port_position_pf ON portfolio_position(portfolio_id);

-- ========== 6. 再平衡任务表 ==========
CREATE TABLE IF NOT EXISTS portfolio_rebalance_job (
    id           BIGSERIAL PRIMARY KEY,
    job_id       VARCHAR(64) NOT NULL UNIQUE,
    portfolio_id VARCHAR(64) NOT NULL,
    trigger_type VARCHAR(16) NOT NULL,
    status       VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    preview_json TEXT,
    execute_mode VARCHAR(16) NOT NULL DEFAULT 'MANUAL',
    diff_total   DECIMAL(20,8),
    cost_est     DECIMAL(20,8),
    executed_at  TIMESTAMP,
    create_time  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted      SMALLINT DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_rb_portfolio ON portfolio_rebalance_job(portfolio_id, status);

-- ========== 7. 事中风控规则表 ==========
CREATE TABLE IF NOT EXISTS intraday_risk_rule (
    id              BIGSERIAL PRIMARY KEY,
    rule_id         VARCHAR(64) NOT NULL UNIQUE,
    name            VARCHAR(128) NOT NULL,
    rule_type       VARCHAR(32) NOT NULL,           -- POSITION_LIMIT/LOSS_LIMIT/FUND_RISK/ORDER_RATE/VAR_LIMIT
    enabled         BOOLEAN NOT NULL DEFAULT TRUE,
    account_id      VARCHAR(64),                    -- 空=全部账户
    strategy_id     VARCHAR(64),                    -- 空=全部策略
    symbol          VARCHAR(64),                    -- 空=全部标的
    threshold_params TEXT,                          -- JSON字符串，如 {"maxLoss":50000,"maxLossRatio":0.03}
    action_level    VARCHAR(16) NOT NULL DEFAULT 'WARN', -- WARN/BLOCK/FORCE_CLOSE
    cooldown_sec    INT NOT NULL DEFAULT 60,        -- 同规则同作用域触发冷却时间
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_intraday_rule_enabled ON intraday_risk_rule(enabled) WHERE enabled AND deleted=0;

-- ========== 8. 处置执行记录表 ==========
CREATE TABLE IF NOT EXISTS risk_disposition_log (
    id             BIGSERIAL PRIMARY KEY,
    disposition_id VARCHAR(64) NOT NULL UNIQUE,
    trigger_rule   VARCHAR(64) NOT NULL,            -- 触发的 rule_id
    scope_type     VARCHAR(16) NOT NULL,            -- ACCOUNT/STRATEGY/SYMBOL
    scope_id       VARCHAR(64) NOT NULL,
    action         VARCHAR(32) NOT NULL,            -- WARN/BLOCK_OPEN/FORCE_CLOSE
    params         TEXT,                            -- 处置参数JSON字符串
    result         VARCHAR(32) NOT NULL DEFAULT 'PENDING', -- PENDING/SUCCESS/FAILED/REJECTED/RELEASED
    confirm_by     VARCHAR(64),
    message        VARCHAR(512),
    create_time    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted        SMALLINT DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_disposition_scope ON risk_disposition_log(scope_type, scope_id, create_time);

-- ========== 9. VaR 计算记录表 ==========
CREATE TABLE IF NOT EXISTS var_calculation (
    id            BIGSERIAL PRIMARY KEY,
    calc_id       VARCHAR(64) NOT NULL UNIQUE,
    scope_type    VARCHAR(16) NOT NULL,
    scope_id      VARCHAR(64) NOT NULL,
    method        VARCHAR(24) NOT NULL DEFAULT 'HISTORICAL',
    confidence    DECIMAL(6,4) NOT NULL DEFAULT 0.9500,
    horizon_days  INT NOT NULL DEFAULT 1,
    var_value     DECIMAL(20,8) NOT NULL,
    pnl_hist_json TEXT,
    calc_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(scope_type, scope_id, calc_time)
);
CREATE INDEX IF NOT EXISTS idx_var_scope ON var_calculation(scope_type, scope_id, calc_time DESC);

-- ========== 10. 异常交易告警表 ==========
CREATE TABLE IF NOT EXISTS abnormal_trade_alert (
    id          BIGSERIAL PRIMARY KEY,
    alert_id    VARCHAR(64) NOT NULL UNIQUE,
    rule_code   VARCHAR(32) NOT NULL,
    account_id  VARCHAR(64),
    strategy_id VARCHAR(64),
    symbol      VARCHAR(32),
    score       DECIMAL(8,4) NOT NULL,
    detail_json TEXT NOT NULL DEFAULT '{}',
    severity    VARCHAR(8) NOT NULL DEFAULT 'INFO',
    status      VARCHAR(16) NOT NULL DEFAULT 'OPEN',
    handler     VARCHAR(64),
    handle_note VARCHAR(512),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted     SMALLINT DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_alert_status ON abnormal_trade_alert(status, severity, create_time DESC);

-- ========== 11. 告警规则表 ==========
CREATE TABLE IF NOT EXISTS alert_rule (
    id            BIGSERIAL PRIMARY KEY,
    rule_id       VARCHAR(64) NOT NULL UNIQUE,
    name          VARCHAR(128) NOT NULL,
    metric_key    VARCHAR(64) NOT NULL,
    condition     VARCHAR(16) NOT NULL,
    threshold     DECIMAL(20,8) NOT NULL,
    window_sec    INT NOT NULL DEFAULT 60,
    severity      VARCHAR(8) NOT NULL DEFAULT 'WARN',
    channels_json TEXT NOT NULL DEFAULT '["log"]',
    enabled       BOOLEAN NOT NULL DEFAULT TRUE,
    create_time   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted       SMALLINT DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_alert_rule_enabled ON alert_rule(enabled) WHERE enabled AND deleted=0;

-- ========== 12. 告警事件表 ==========
CREATE TABLE IF NOT EXISTS alert_event (
    id          BIGSERIAL PRIMARY KEY,
    event_id    VARCHAR(64) NOT NULL UNIQUE,
    rule_id     VARCHAR(64),
    metric_key  VARCHAR(64) NOT NULL,
    value       VARCHAR(64) NOT NULL,
    severity    VARCHAR(8) NOT NULL,
    message     VARCHAR(512),
    status      VARCHAR(16) NOT NULL DEFAULT 'OPEN',
    ack_by      VARCHAR(64),
    ack_time    TIMESTAMP,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_alert_event_status ON alert_event(status, severity, create_time DESC);

-- ========== 13. 策略加密表 ==========
CREATE TABLE IF NOT EXISTS strategy_crypto (
    id          BIGSERIAL PRIMARY KEY,
    strategy_id VARCHAR(64) NOT NULL UNIQUE,
    version     INT NOT NULL DEFAULT 1,
    algo        VARCHAR(16) NOT NULL DEFAULT 'AES_256_GCM',
    ciphertext  TEXT NOT NULL,
    key_id      VARCHAR(64) NOT NULL,
    checksum    VARCHAR(64) NOT NULL,
    uploaded_by VARCHAR(64),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted     SMALLINT DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_crypto_strategy ON strategy_crypto(strategy_id, version);

-- ========== 14. 策略操作审计日志表 ==========
CREATE TABLE IF NOT EXISTS strategy_audit_log (
    id          BIGSERIAL PRIMARY KEY,
    strategy_id VARCHAR(64) NOT NULL,
    action      VARCHAR(32) NOT NULL,
    operator    VARCHAR(64) NOT NULL,
    detail_json TEXT NOT NULL DEFAULT '{}',
    ip_addr     VARCHAR(64),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_audit_strategy ON strategy_audit_log(strategy_id, create_time DESC);

-- ========== 15. 清算批次表 ==========
CREATE TABLE IF NOT EXISTS settlement_batch (
    id            BIGSERIAL PRIMARY KEY,
    batch_id      VARCHAR(64) NOT NULL UNIQUE,
    trade_date    DATE NOT NULL,
    status        VARCHAR(16) NOT NULL DEFAULT 'CREATED',
    account_count INT NOT NULL DEFAULT 0,
    total_pnl     DECIMAL(20,8),
    total_fee     DECIMAL(20,8),
    total_diff    INT NOT NULL DEFAULT 0,
    started_at    TIMESTAMP,
    finished_at   TIMESTAMP,
    error_message VARCHAR(512),
    create_time   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(trade_date)
);

-- ========== 16. 对账差异工单表 ==========
CREATE TABLE IF NOT EXISTS settlement_diff_item (
    id            BIGSERIAL PRIMARY KEY,
    diff_id       VARCHAR(64) NOT NULL UNIQUE,
    batch_id      VARCHAR(64) NOT NULL,
    account_id    VARCHAR(64) NOT NULL,
    field_name    VARCHAR(64) NOT NULL,
    system_value  DECIMAL(20,8),
    counter_value DECIMAL(20,8),
    diff_value    DECIMAL(20,8),
    tolerance     DECIMAL(20,8) NOT NULL DEFAULT 0,
    status        VARCHAR(16) NOT NULL DEFAULT 'OPEN',
    adjust_by     VARCHAR(64),
    adjust_note   VARCHAR(512),
    create_time   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time   TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_diff_batch ON settlement_diff_item(batch_id, status);

-- ========== 17. 性能降级开关表 ==========
CREATE TABLE IF NOT EXISTS degrade_switch (
    id          BIGSERIAL PRIMARY KEY,
    switch_key  VARCHAR(64) NOT NULL UNIQUE,
    label       VARCHAR(128),
    level       VARCHAR(16) NOT NULL DEFAULT 'OFF',
    operator    VARCHAR(64),
    reason      VARCHAR(256),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted     SMALLINT DEFAULT 0
);