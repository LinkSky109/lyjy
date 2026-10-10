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
    echo "[obs] 抓取目标 -> $TARGET"
    docker compose -f "$COMPOSE" up -d
    echo "[obs] Prometheus http://localhost:9090  Grafana http://localhost:3000 (admin/${GF_ADMIN_PASSWORD:-lhjypt})"
    ;;
  down)
    docker compose -f "$COMPOSE" down
    ;;
  status)
    docker ps --filter name=lhjypt-prometheus --filter name=lhjypt-grafana \
      --format '{{.Names}}\t{{.Status}}\t{{.Ports}}'
    echo "[obs] Prometheus targets:"
    curl -s -m 5 "http://localhost:9090/api/v1/targets?state=active" | python3 -c '
import sys, json
targets = json.load(sys.stdin).get("data", {}).get("activeTargets", [])
if not targets:
    print("  （无 / Prometheus 未就绪）")
for t in targets:
    print("  ", t["labels"].get("job"), t["scrapeUrl"], t["health"])
' 2>/dev/null || echo "  （Prometheus 未就绪）"
    ;;
  *)
    echo "usage: $0 up|down|status"; exit 1;;
esac
