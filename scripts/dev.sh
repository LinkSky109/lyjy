#!/usr/bin/env bash
# ============================================================================
# lhjypt 单人一键运维脚本（macOS/Linux，需 Docker + JDK17 + Maven + Node）
#
#   ./scripts/dev.sh up         启动中间件 + 应用增量SQL + 构建并启动后端/前端
#   ./scripts/dev.sh status     查看中间件/后端/前端健康状态
#   ./scripts/dev.sh down       停止后端/前端/中间件（保留数据库卷）
#   ./scripts/dev.sh reset-db   删除数据库卷并重建（全量初始化，数据归零）
#   ./scripts/dev.sh logs [svc] 跟踪日志（backend | frontend | middleware）
# ============================================================================
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
COMPOSE_FILE="$ROOT/config/docker-compose.yaml"

# Lima Docker（本机默认）；可用 LHJYPT_DOCKER_HOST 覆盖
if [ -n "${LHJYPT_DOCKER_HOST:-}" ]; then
  export DOCKER_HOST="$LHJYPT_DOCKER_HOST"
  export DOCKER_API_VERSION=1.44
elif [ -S "$HOME/.lima/tuna-docker/sock/docker.sock" ]; then
  export DOCKER_HOST="unix://$HOME/.lima/tuna-docker/sock/docker.sock"
  export DOCKER_API_VERSION=1.44
fi
export TESTCONTAINERS_RYUK_DISABLED=true

PG="docker exec -i lhjypt-postgres psql -U lhjypt -d lhjypt"
BACKEND_PID_FILE="$ROOT/.runtime/backend.pid"
FRONTEND_PID_FILE="$ROOT/.runtime/frontend.pid"

start_middleware() {
  local all_present=1
  for c in lhjypt-postgres lhjypt-clickhouse lhjypt-kafka lhjypt-redis lhjypt-zookeeper; do
    docker inspect "$c" >/dev/null 2>&1 || all_present=0
  done
  if [ "$all_present" = 1 ]; then
    echo "[dev] 中间件容器已存在，直接复用（如配置变更需 reset-db）"
  else
    echo "[dev] 创建中间件容器..."
    docker compose -f "$COMPOSE_FILE" up -d
  fi
  echo "[dev] 等待中间件健康..."
  for i in $(seq 1 60); do
    ok=1
    for c in lhjypt-postgres lhjypt-clickhouse lhjypt-kafka lhjypt-redis; do
      st="$(docker inspect -f '{{.State.Health.Status}}' "$c" 2>/dev/null || echo none)"
      [ "$st" = "healthy" ] || ok=0
    done
    [ "$ok" = 1 ] && { echo "[dev] 中间件 4 容器 healthy"; break; }
    sleep 2
  done
  [ "$ok" = 1 ] || { echo "[dev] 中间件未全部健康"; docker ps --format '{{.Names}} {{.Status}}'; exit 1; }
}

apply_sql() {
  echo "[dev] 应用增量初始化 SQL（幂等，可重复执行）..."
  for f in scripts/init-p1.sql scripts/init-p2.sql scripts/migration/p2_*.sql; do
    echo "      <- $f"
    docker exec -i lhjypt-postgres psql -U lhjypt -d lhjypt < "$ROOT/$f" >/dev/null
  done
}

backend_up() {
  if curl -s -m 2 http://localhost:8081/actuator/health 2>/dev/null | grep -q '"status":"UP"'; then
    echo "[dev] 后端已在运行 (8081)"
    return 0
  fi
  JAR="$ROOT/core/target/core-0.1.0-SNAPSHOT.jar"
  if [ "${LHJYPT_REBUILD:-0}" = "1" ] || [ ! -f "$JAR" ]; then
    echo "[dev] 构建后端 jar（跳过测试，可用 LHJYPT_REBUILD=1 强制）..."
    (cd "$ROOT" && mvn -q package -DskipTests)
  fi
  mkdir -p "$ROOT/.runtime" "$ROOT/logs"
  echo "[dev] 启动后端 -> logs/backend.log"
  nohup java -jar "$JAR" > "$ROOT/logs/backend.log" 2>&1 &
  echo $! > "$BACKEND_PID_FILE"
  for i in $(seq 1 60); do
    if curl -s -m 2 http://localhost:8081/actuator/health 2>/dev/null | grep -q '"status":"UP"'; then
      echo "[dev] 后端 UP (8081)"
      return 0
    fi
    sleep 2
  done
  echo "[dev] 后端启动超时，见 logs/backend.log"; exit 1
}

frontend_up() {
  if curl -s -m 2 -o /dev/null -w '%{http_code}' http://localhost:5173/ 2>/dev/null | grep -q 200; then
    echo "[dev] 前端已在运行 (5173)"
    return 0
  fi
  [ -d "$ROOT/web/node_modules" ] || { echo "[dev] 安装前端依赖..."; (cd "$ROOT/web" && npm install); }
  echo "[dev] 启动前端 -> logs/frontend.log"
  (cd "$ROOT/web" && nohup npm run dev > "$ROOT/logs/frontend.log" 2>&1 & echo $! > "$FRONTEND_PID_FILE")
  for i in $(seq 1 30); do
    if curl -s -m 2 -o /dev/null -w '%{http_code}' http://localhost:5173/ 2>/dev/null | grep -q 200; then
      echo "[dev] 前端 UP (5173, proxy -> 8081)"
      return 0
    fi
    sleep 1
  done
  echo "[dev] 前端启动超时，见 logs/frontend.log"; exit 1
}

stop_stack() {
  [ -f "$BACKEND_PID_FILE" ] && kill "$(cat "$BACKEND_PID_FILE")" 2>/dev/null || true
  [ -f "$FRONTEND_PID_FILE" ] && kill "$(cat "$FRONTEND_PID_FILE")" 2>/dev/null || true
  pkill -f 'core-0.1.0-SNAPSHOT.jar' 2>/dev/null || true
  pkill -f 'vite' 2>/dev/null || true
  echo "[dev] 前后端已停止"
}

status() {
  echo "== 中间件 =="
  docker ps --filter "name=lhjypt-" --format '{{.Names}}\t{{.Status}}' || echo "Docker 未连接"
  echo "== 后端 =="
  curl -s -m 2 http://localhost:8081/actuator/health || echo "8081 未响应"
  echo
  echo "== 前端 =="
  curl -s -m 2 -o /dev/null -w 'http=%{http_code}\n' http://localhost:5173/ || echo "5173 未响应"
}

case "${1:-up}" in
  up)       start_middleware; apply_sql; backend_up; frontend_up; status;;
  status)   status;;
  down)     stop_stack
            for c in lhjypt-postgres lhjypt-clickhouse lhjypt-kafka lhjypt-zookeeper lhjypt-redis; do
              docker stop "$c" >/dev/null 2>&1 || true
            done
            echo "[dev] 前后端与中间件已停止（数据卷保留）";;
  reset-db) stop_stack
            for c in lhjypt-postgres lhjypt-clickhouse lhjypt-kafka lhjypt-zookeeper lhjypt-redis; do
              docker rm -f "$c" >/dev/null 2>&1 || true
            done
            docker volume rm lhjypt_pgdata lhjypt_chdata lhjypt_redisdata >/dev/null 2>&1 || true
            start_middleware; apply_sql
            echo "[dev] 数据库已重建并初始化";;
  logs)     case "${2:-backend}" in
              backend) tail -f "$ROOT/logs/backend.log";;
              frontend) tail -f "$ROOT/logs/frontend.log";;
              *) docker logs -f "${2:-}";;
            esac;;
  *) echo "用法: $0 {up|status|down|reset-db|logs [backend|frontend|<container>]}"; exit 1;;
esac