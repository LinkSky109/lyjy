-- TASK-P1-005 多柜台多通道增量迁移

BEGIN;

ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS gateway_id VARCHAR(64);

CREATE INDEX IF NOT EXISTS idx_orders_gateway
    ON orders(gateway_id);

ALTER TABLE trade_record
    ADD COLUMN IF NOT EXISTS gateway_id VARCHAR(64);

ALTER TABLE trade_record
    ADD COLUMN IF NOT EXISTS gateway_session_id VARCHAR(64);

ALTER TABLE trade_record
    DROP CONSTRAINT IF EXISTS trade_record_trade_id_key;

DROP INDEX IF EXISTS uq_trade_gateway_trade;

CREATE UNIQUE INDEX IF NOT EXISTS uq_trade_gateway_session_trade
    ON trade_record(gateway_id, gateway_session_id, trade_id);

COMMIT;
