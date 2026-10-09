#!/usr/bin/env bash
# ============================================================================
# lhjypt 健康巡检（P3-003）
#
#   ./scripts/healthcheck.sh            巡检并输出（异常退出码 1，便于 cron/launchd 捕获）
#   LHJYPT_HEALTH_WEBHOOK=<url> ./scripts/healthcheck.sh   异常时 POST 通知（钉钉/企业微信/generic）
#
# 检查：中间件容器 → 后端健康与子组件 → 前端 → 数据面（表/行情新鲜度/磁盘）
# 日志：logs/healthcheck.log（追加）；报告：stdout
# ============================================================================
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BASE="${LHJYPT_BASE:-http://localhost:8081}"
LOG="$ROOT/logs/healthcheck.log"
mkdir -p "$ROOT/logs"

if [ -n "${LHJYPT_DOCKER_HOST:-}" ]; then
  export DOCKER_HOST="$LHJYPT_DOCKER_HOST"; export DOCKER_API_VERSION=1.44
elif [ -S "$HOME/.lima/tuna-docker/sock/docker.sock" ]; then
  export DOCKER_HOST="unix://$HOME/.lima/tuna-docker/sock/docker.sock"; export DOCKER_API_VERSION=1.44
fi

FAILS=0
WARNS=0
LINES=()

record() { # level name detail
  local level="$1" name="$2" detail="$3"
  case "$level" in
    FAIL) FAILS=$((FAILS + 1));;
    WARN) WARNS=$((WARNS + 1));;
  esac
  LINES+=("[$level] $name - $detail")
  printf '[%s] %-28s %s\n' "$level" "$name" "$detail"
}

# 1) 中间件容器
for c in lhjypt-postgres lhjypt-clickhouse lhjypt-kafka lhjypt-redis lhjypt-zookeeper; do
  state="$(docker inspect -f '{{.State.Status}}' "$c" 2>/dev/null || echo missing)"
  health="$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}n/a{{end}}' "$c" 2>/dev/null || echo missing)"
  if [ "$state" = "running" ] && { [ "$health" = "healthy" ] || [ "$health" = "n/a" ]; }; then
    record OK "$c" "running/$health"
  elif [ "$state" = "missing" ]; then
    record FAIL "$c" "容器不存在（执行 ./scripts/dev.sh up）"
  else
    record FAIL "$c" "state=$state health=$health"
  fi
done

# 2) 后端健康 + 子组件
health_json="$(curl -s -m 5 "$BASE/actuator/health" 2>/dev/null || true)"
if echo "$health_json" | grep -q '"status":"UP"'; then
  for sub in primaryDataSource clickHouseDataSource redis; do
    if echo "$health_json" | grep -q "\"$sub\":{\"status\":\"UP\""; then
      record OK "backend.$sub" "UP"
    else
      record WARN "backend.$sub" "未见 UP（可能未启用）"
    fi
  done
  record OK "backend" "健康 UP"
else
  record FAIL "backend" "8081 未响应或无 UP（执行 ./scripts/dev.sh up）"
fi

# 3) 前端
fe_code="$(curl -s -m 4 -o /dev/null -w '%{http_code}' http://localhost:5173/ 2>/dev/null || echo 000)"
if [ "${fe_code}" = "200" ]; then
  record OK "frontend" "http=200"
else
  record WARN "frontend" "http=${fe_code}（前端未运行可用 dev.sh up）"
fi

# 4) 数据面：关键表 + 行情新鲜度 + 磁盘
if [ "$(docker inspect -f '{{.State.Status}}' lhjypt-postgres 2>/dev/null)" = "running" ]; then
  missing="$(docker exec lhjypt-postgres psql -U lhjypt -d lhjypt -t -A -c \
    "SELECT string_agg(t,',') FROM (VALUES ('strategy_info'),('orders'),('fund_account'),('tenant')) v(t)
     WHERE to_regclass('public.'||t) IS NULL;" 2>/dev/null)"
  if [ -z "${missing}" ]; then
    record OK "pg.tables" "关键表齐全"
  else
    record FAIL "pg.tables" "缺表: ${missing}"
  fi
fi

if [ "$(docker inspect -f '{{.State.Status}}' lhjypt-clickhouse 2>/dev/null)" = "running" ]; then
  lag="$(docker exec lhjypt-clickhouse clickhouse-client -q \
    "SELECT if(count()=0, -1, dateDiff('hour', max(bar_time), now())) FROM lhjypt.bar_data;" 2>/dev/null || echo -2)"
  if [ "$lag" = "-1" ]; then
    record WARN "ch.bar_data" "无行情数据（可 POST /api/market/real/refresh）"
  elif [ "$lag" = "-2" ]; then
    record WARN "ch.bar_data" "查询失败"
  elif [ "$lag" -le 24 ]; then
    record OK "ch.bar_data" "最新 bar 距今 ${lag}h"
  else
    record WARN "ch.bar_data" "行情滞后 ${lag}h（>24h，检查定时刷新）"
  fi
fi

disk_use="$(df -h / | awk 'NR==2 {gsub("%","",$5); print $5}')"
if [ -n "$disk_use" ] && [ "$disk_use" -ge 90 ]; then
  record WARN "disk" "根分区使用 ${disk_use}%（≥90%）"
else
  record OK "disk" "根分区使用 ${disk_use:-?}%"
fi

# 汇总
SUMMARY="健康巡检: OK=$(( $(printf '%s\n' "${LINES[@]}" | grep -c '^\[OK\]') )) WARN=$WARNS FAIL=$FAILS"
echo "$SUMMARY"
{
  echo "=== $(date '+%Y-%m-%d %H:%M:%S') ==="
  printf '%s\n' "${LINES[@]}"
  echo "$SUMMARY"
} >> "$LOG"

# 异常通知（可选 webhook）
if [ "$FAILS" -gt 0 ] && [ -n "${LHJYPT_HEALTH_WEBHOOK:-}" ]; then
  payload="$(python3 - <<PY
import json
print(json.dumps({"msgtype":"text","text":{"content":"[lhjypt] 健康巡检异常 FAIL=$FAILS\n" + "\n".join([l for l in '''$(printf '%s\n' "${LINES[@]}" | grep '^\[FAIL\]' | head -5)'''.splitlines()])}}))
PY
)"
  curl -s -m 5 -X POST "$LHJYPT_HEALTH_WEBHOOK" -H 'Content-Type: application/json' -d "$payload" >/dev/null 2>&1 || true
fi

[ "$FAILS" -eq 0 ] && exit 0 || exit 1