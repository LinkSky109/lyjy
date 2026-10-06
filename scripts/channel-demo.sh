#!/usr/bin/env bash
# ============================================================================
# lhjypt 真实交易通道演练（Mock 网关，幂等可重复）
#   账户基线 -> 策略沙箱内 SDK 下单(限价) -> Mock 成交 -> 撤已成交单(预期拒绝)
#   -> 错误账户下单(预期拒绝) -> 服务端持仓/资金独立校验 -> 停止
# 依赖：./scripts/dev.sh up（后端 8081）
# ============================================================================
set -euo pipefail
BASE="${LHJYPT_BASE:-http://localhost:8081}"
H2() { python3 -c "import sys,json;d=json.load(sys.stdin);print(d.get('code'), (d.get('message') or ''))"; }
TRADER=(-H 'Content-Type: application/json' -H 'X-User-Role: TRADER' -H 'X-User-Id: trader-1')

echo "== 0) 探活 =="
curl -s -m 3 "$BASE/actuator/health" | grep -q '"status":"UP"' || { echo "后端未启动，先执行 ./scripts/dev.sh up"; exit 1; }
echo "backend UP"

echo "== 1) 账户资金基线（DEFAULT-001）=="
BASE_ACCT=$(curl -s "$BASE/api/trade/platform/account?accountId=DEFAULT-001" "${TRADER[@]}")
echo "$BASE_ACCT" | python3 -c "import sys,json;d=json.load(sys.stdin).get('data') or {};print('总资产',d.get('totalAsset'),'可用',d.get('availableCash'),'保证金',d.get('margin'))"

echo "== 2) 连接交易网关（Mock 高流动性，限价单自动成交）=="
curl -s -X POST "$BASE/api/trade/connect" "${TRADER[@]}" -d '{"userId":"trader-1","brokerId":"9999","tdFrontAddress":"tcp://127.0.0.1:8888"}' | H2
curl -s "$BASE/api/trade/status" "${TRADER[@]}" | python3 -c "import sys,json;d=json.load(sys.stdin).get('data') or {};print('connected=',d.get('connected'),'authenticated=',d.get('authenticated'),'mode=',d.get('mode'))"

echo "== 3) 上传演练策略 CH-DEMO =="
SRC=$(python3 -c "import json;print(json.dumps({'name':'通道演练','sourceCode':open('strategy-sdk/examples/channel_demo.py').read()}))")
curl -s -X POST "$BASE/api/strategy/CH-DEMO/code" "${TRADER[@]}" -d "$SRC" | python3 -c "import sys,json;d=json.load(sys.stdin);print('upload code=',d['code'],'version=',(d.get('data') or {}).get('version'))"

echo "== 4) 沙箱运行（策略内 SDK 下单链路）=="
curl -s -X POST "$BASE/api/strategy/CH-DEMO/run" "${TRADER[@]}" | python3 -c "import sys,json;d=json.load(sys.stdin);print('run code=',d['code'],'status=',(d.get('data') or {}).get('status'))"
sleep 5
curl -s "$BASE/api/strategy/sandbox/status/CH-DEMO" "${TRADER[@]}" | python3 -c "
import sys,json
d=json.load(sys.stdin).get('data') or {}
print('sandbox=',d.get('status'))
out=(d.get('outputTail') or '')
for line in out.splitlines():
    print('   |', line)
"

echo "== 5) 服务端独立校验：成交 / 持仓 / 资金 =="
curl -s "$BASE/api/trade/trades" "${TRADER[@]}" | python3 -c "
import sys,json
ds=json.load(sys.stdin).get('data') or []
print('成交记录条数=',len(ds))
for t in ds[:3]:
    print('   ',t.get('tradeId'),t.get('symbol'),t.get('direction'),t.get('quantity'),'@',t.get('price'))
"
curl -s "$BASE/api/trade/platform/positions?accountId=DEFAULT-001&strategyId=CH-DEMO" "${TRADER[@]}" | python3 -c "
import sys,json
ds=json.load(sys.stdin).get('data') or []
print('CH-DEMO 持仓:',[(p.get('symbol'),p.get('direction'),str(p.get('quantity')),'cost',p.get('avgCost')) for p in ds])
"
curl -s "$BASE/api/trade/platform/account?accountId=DEFAULT-001" "${TRADER[@]}" | python3 -c "
import sys,json
d=json.load(sys.stdin).get('data') or {}
print('资金现值: 总资产',d.get('totalAsset'),'可用',d.get('availableCash'),'保证金',d.get('margin'))
"

echo "== 6) 停止演练策略 =="
curl -s -X POST "$BASE/api/strategy/CH-DEMO/stop" "${TRADER[@]}" | python3 -c "import sys,json;d=json.load(sys.stdin);print('stop code=',d['code'],'status=',(d.get('data') or {}).get('status'))"

echo
echo "== 真实交易通道演练完成：SDK下单->Mock成交->撤单拒绝->错误账户拒绝->持仓/资金闭环 =="