#!/usr/bin/env bash
# ============================================================================
# lhjypt 行情信号实盘联动演练（OBS-009，幂等可重复）
#   真实行情 -> 策略信号(SMA交叉) -> 自动下单 -> Mock成交 -> 持仓/资金校验
# 依赖：./scripts/dev.sh up（后端 8081）+ 真实行情已入库（refresh 兜底）
# ============================================================================
set -euo pipefail
BASE="${LHJYPT_BASE:-http://localhost:8081}"
TRADER=(-H 'Content-Type: application/json' -H 'X-User-Role: TRADER' -H 'X-User-Id: trader-1')
ADMIN=(-H 'Content-Type: application/json' -H 'X-User-Role: ADMIN' -H 'X-User-Id: admin')

echo "== 0) 探活 =="
curl -s -m 3 "$BASE/actuator/health" | grep -q '"status":"UP"' || { echo "后端未启动，先 ./scripts/dev.sh up"; exit 1; }
echo "backend UP"

echo "== 1) 连接交易网关（Mock）=="
curl -s -X POST "$BASE/api/trade/connect" "${TRADER[@]}" -d '{"userId":"trader-1","brokerId":"9999","tdFrontAddress":"tcp://127.0.0.1:8888"}' >/dev/null
curl -s "$BASE/api/trade/status" "${TRADER[@]}" | python3 -c "import sys,json;d=json.load(sys.stdin).get('data') or {};print('connected=',d.get('connected'),'authenticated=',d.get('authenticated'),'mode=',d.get('mode'))"

echo "== 2) 兜底刷新真实行情（已有则跳过）=="
cnt=$(docker exec lhjypt-clickhouse clickhouse-client -q "SELECT count() FROM lhjypt.bar_data WHERE symbol='BTC_USDT' AND frequency='1h';" 2>/dev/null || echo 0)
if [ "$cnt" -lt 50 ]; then
  curl -s -X POST "$BASE/api/market/real/refresh?currencyPair=BTC_USDT&interval=1h&limit=200" "${ADMIN[@]}" | python3 -c "import sys,json;print('refresh 新增=',json.load(sys.stdin).get('data'))"
fi
echo "真实 K 线数(BTC_USDT@1h)=$cnt"

echo "== 3) 上传信号交易策略 SIG-TRADE =="
SRC=$(python3 -c "import json;print(json.dumps({'name':'信号交易','sourceCode':open('strategy-sdk/examples/signal_trade.py').read()}))")
curl -s -X POST "$BASE/api/strategy/SIG-TRADE/code" "${TRADER[@]}" -d "$SRC" | python3 -c "import sys,json;d=json.load(sys.stdin);print('upload code=',d['code'],'version=',(d.get('data') or {}).get('version'))"

echo "== 4) 运行策略（真实行情驱动信号与下单，观察 12s）=="
curl -s -X POST "$BASE/api/strategy/SIG-TRADE/run" "${TRADER[@]}" | python3 -c "import sys,json;d=json.load(sys.stdin);print('run code=',d['code'],'status=',(d.get('data') or {}).get('status'))"
sleep 16
curl -s "$BASE/api/strategy/sandbox/status/SIG-TRADE" "${TRADER[@]}" | python3 -c "
import sys,json
d=json.load(sys.stdin).get('data') or {}
print('sandbox=',d.get('status'))
for line in (d.get('outputTail') or '').splitlines()[:20]:
    print('   |', line)
"

echo "== 5) 服务端校验：成交 / 持仓 / 资金 =="
curl -s "$BASE/api/trade/trades" "${TRADER[@]}" | python3 -c "
import sys,json
ds=json.load(sys.stdin).get('data') or []
print('最近成交条数=',len(ds))
for t in ds[-4:]:
    print('   ',t.get('tradeId'),t.get('symbol'),t.get('direction'),t.get('quantity'),'@',t.get('price'))
"
curl -s "$BASE/api/trade/platform/positions?accountId=DEFAULT-001&strategyId=SIG-TRADE" "${TRADER[@]}" | python3 -c "
import sys,json
ds=json.load(sys.stdin).get('data') or []
print('SIG-TRADE 持仓:',[(p.get('symbol'),p.get('direction'),str(p.get('quantity')),'cost',p.get('avgCost')) for p in ds] or '（空——信号未开仓或已平）')
"

echo "== 6) 停止策略 =="
curl -s -X POST "$BASE/api/strategy/SIG-TRADE/stop" "${TRADER[@]}" | python3 -c "import sys,json;d=json.load(sys.stdin);print('stop code=',d['code'],'status=',(d.get('data') or {}).get('status'))"

echo
echo "== 行情信号实盘联动演练完成：真实K线 -> SMA交叉信号 -> 自动下单 -> Mock成交 =="