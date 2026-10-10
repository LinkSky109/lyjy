#!/usr/bin/env bash
# ============================================================================
# lhjypt 本机观测栈（P4-003）
#
#   ./scripts/obs.sh up       启动 Prometheus(9090) + Grafana(3000)（生成抓取配置）
#   ./scripts/obs.sh down     停止并移除（保留数据卷）
#   ./scripts/obs.sh status   容器状态 + Prometheus 抓取目标
#
# 抓取目标：LHJYPT_SCRAPE_TARGET（默认 host.lima.internal:8081；Docker Desktop 用 host.docker.internal:8081）
# Grafana 登录：admin / ${GF_ADMIN_PASSWORD:-lhjypt}
# ============================================================================
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OBS_DIR="$ROOT/config/observability"
COMPOSE="$OBS_DIR/docker-compose.yaml"
TARGET="${LHJYPT_SCRAPE_TARGET:-host.lima.internal:8081}"
# 告警外发目标：默认内置 sink（验证用）；钉钉/企业微信用转发网关（先转换格式）
WEBHOOK="${LHJYPT_ALERT_WEBHOOK:-http://lhjypt-alert-sink:8080/}"

if [ -n "${LHJYPT_DOCKER_HOST:-}" ]; then
  export DOCKER_HOST="$LHJYPT_DOCKER_HOST"; export DOCKER_API_VERSION=1.44
elif [ -S "$HOME/.lima/tuna-docker/sock/docker.sock" ]; then
  export DOCKER_HOST="unix://$HOME/.lima/tuna-docker/sock/docker.sock"; export DOCKER_API_VERSION=1.44
fi

action="${1:-up}"
case "$action" in
  up)
    mkdir -p "$OBS_DIR/.generated"
    sed "s|__TARGET__|${TARGET}|g" "$OBS_DIR/prometheus.yml.tmpl" > "$OBS_DIR/.generated/prometheus.yml"
    webhook_esc="$(printf '%s' "$WEBHOOK" | sed 's/&/\\&/g')"
    sed "s|__WEBHOOK__|${webhook_esc}|g" "$OBS_DIR/alertmanager.yml.tmpl" > "$OBS_DIR/.generated/alertmanager.yml"
    echo "[obs] 抓取目标 -> $TARGET ；告警外发 -> $WEBHOOK"
    # --force-recreate：.generated 由单文件 bind mount 挂载，重建确保绑定最新配置（避免换 inode 后容器读旧/丢失）
    docker compose -f "$COMPOSE" up -d --force-recreate
    echo "[obs] Prometheus http://localhost:9090  Alertmanager http://localhost:9093  Grafana http://localhost:3000 (admin/${GF_ADMIN_PASSWORD:-lhjypt})"
    ;;
  down)
    docker compose -f "$COMPOSE" down
    ;;
  status)
    docker ps --filter name=lhjypt-prometheus --filter name=lhjypt-grafana \
      --filter name=lhjypt-alertmanager --filter name=lhjypt-alert-sink \
      --format '{{.Names}}\t{{.Status}}'
    echo "[obs] Prometheus targets:"
    curl -s -m 5 "http://localhost:9090/api/v1/targets?state=active" | python3 -c '
import sys, json
targets = json.load(sys.stdin).get("data", {}).get("activeTargets", [])
if not targets:
    print("  （无 / Prometheus 未就绪）")
for t in targets:
    print("  ", t["labels"].get("job"), t["scrapeUrl"], t["health"])
' 2>/dev/null || echo "  （Prometheus 未就绪）"
    echo "[obs] Alertmanager 活动告警:"
    curl -s -m 5 "http://localhost:9093/api/v2/alerts" | python3 -c '
import sys, json
alerts = json.load(sys.stdin)
if not alerts:
    print("  （无）")
for a in alerts:
    labels = a.get("labels", {})
    print("  ", a.get("status", {}).get("state"), labels.get("alertname"), "severity=" + str(labels.get("severity")))
' 2>/dev/null || echo "  （Alertmanager 未就绪）"
    ;;
  *)
    echo "usage: $0 up|down|status"; exit 1;;
esac
