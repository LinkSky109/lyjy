-- 初始化 P2 新增表结构（增量，可重复执行）
-- P2-002：策略水印与运行环境绑定表，追加到 init-p2.sql 末尾

-- ========== 5. 策略水印与运行环境绑定 ==========
CREATE TABLE IF NOT EXISTS strategy_watermark (
    id             BIGSERIAL PRIMARY KEY,
    strategy_id    VARCHAR(64) NOT NULL,
    version        INT NOT NULL DEFAULT 1,
    token          VARCHAR(64) NOT NULL UNIQUE,      -- 水印令牌（溯源键）
    owner          VARCHAR(64) NOT NULL,
    uploaded_by    VARCHAR(64),
    source_hash    VARCHAR(64),                       -- 含水印源码 SHA-256
    env_fingerprint VARCHAR(128),                     -- 运行环境绑定指纹
    create_time    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted        SMALLINT DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_wm_strategy ON strategy_watermark(strategy_id, version);