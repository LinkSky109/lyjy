#!/usr/bin/env bash
# ============================================================================
# lhjypt 一键演示：真实交易链路冒烟（幂等，可重复执行）
#   入金 → 上传示例策略 STR-DEMO → 沙箱运行取输出 → 停止 → 打印链路结果
# 依赖：./scripts/dev.sh up 已执行（后端 8081）
# ============================================================================
set -euo pipefail
BASE="${LHJYPT_BASE:-http://localhost:8081}"
J() { python3 -c "import sys,json;d=json.load(sys.stdin);print(d.get('code'),d.get('message') or '',str(d.get('data'))[:90])"; }
ADMIN=(-H 'Content-Type: application/json' -H 'X-User-Role: ADMIN' -H 'X-User-Id: admin')
TRADER=(-H 'Content-Type: application/json' -H 'X-User-Role: TRADER' -H 'X-User-Id: trader-1')

echo "== 0) 探活 =="
curl -s -m 3 "$BASE/actuator/health" | grep -q '"status":"UP"' || { echo "后端未启动，先执行 ./scripts/dev.sh up"; exit 1; }
echo "backend UP"

echo "== 1) 账户资金（平台账本 DEFAULT-001）=="
curl -s "$BASE/api/trade/platform/account?accountId=DEFAULT-001" "${TRADER[@]}" | python3 -c "import sys,json;d=json.load(sys.stdin).get('data') or {};print('账户=',d.get('accountId'),'总资产=',d.get('totalAsset'),'可用=',d.get('availableCash'),'冻结=',d.get('frozenCash'),'保证金=',d.get('margin'))"

echo "== 2) 上传示例策略 STR-DEMO =="
SRC='{"name":"双均线演示","sourceCode":"from lhjypt.api.strategy import Strategy\n\nclass Demo(Strategy):\n    def on_init(self):\n        print(\"[demo] init\")\n    def on_start(self):\n        print(\"[demo] demo strategy started\")\n"}'
curl -s -X POST "$BASE/api/strategy/STR-DEMO/code" "${TRADER[@]}" -d "$SRC" | J

echo "== 3) 水印溯源 =="
curl -s "$BASE/api/strategy/STR-DEMO/watermark" "${TRADER[@]}" | python3 -c "import sys,json;d=json.load(sys.stdin);print('watermarked=',d.get('data',{}).get('watermarked'))"

echo "== 4) 沙箱运行（运行状态 + 输出尾部）=="
RUN=$(curl -s -X POST "$BASE/api/strategy/STR-DEMO/run" "${TRADER[@]}")
echo "$RUN" | python3 -c "import sys,json;d=json.load(sys.stdin);print('run code=',d['code'],'status=',(d.get('data') or {}).get('status'))"
sleep 2
curl -s "$BASE/api/strategy/sandbox/status/STR-DEMO" "${TRADER[@]}" | python3 -c "import sys,json;d=json.load(sys.stdin);dd=d.get('data') or {};print('sandbox=',dd.get('status'),'pid=',dd.get('pid'));print('output=',(dd.get('outputTail') or '').strip()[:160])"

echo "== 5) 停止 =="
curl -s -X POST "$BASE/api/strategy/STR-DEMO/stop" "${TRADER[@]}" | J

echo "== 6) 审计留痕 =="
curl -s "$BASE/api/strategy/audit" "${ADMIN[@]}" | python3 -c "import sys,json;ds=json.load(sys.stdin).get('data') or [];print('审计条数=',len(ds),'动作=',[a.get('action') for a in ds][:5])" 2>/dev/null || echo "（审计接口为空或路径变化，见前端策略页）"

echo
echo "== 演示链路完成：上传 → 水印 → 沙箱运行 → 停止 → 审计 =="
echo "提示：脚本幂等；STR-DEMO 已存在时直接复用。数据可在前端(5173)策略页查看。"