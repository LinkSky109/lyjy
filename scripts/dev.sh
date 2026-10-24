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

# 本地环境变量（.env 不入库；用于覆盖数据库端口等本机差异）
if [ -f "$ROOT/.env" ]; then
  set -a
  . "$ROOT/.env"
  set +a
fi

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
WEB_PORT="${LHJYPT_WEB_PORT:-5173}"
BACKEND_PORT=8081
BACKEND_PID_FILE="$ROOT/.runtime/backend.pid"
FRONTEND_PID_FILE="$ROOT/.runtime/frontend.pid"

port_pid() {
  lsof -tiTCP:"$1" -sTCP:LISTEN 2>/dev/null | head -1 || true
}

is_our_backend() {
  [ -n "${1:-}" ] && ps -p "$1" -o command= 2>/dev/null | grep -q "core/target/core-.*\.jar"
}

is_our_frontend() {
  [ -n "${1:-}" ] && ps -p "$1" -o command= 2>/dev/null | grep -q "web/node_modules/.bin/vite"
}

wait_port_free() {
  local port="$1" seconds="${2:-15}" i
  for i in $(seq 1 "$seconds"); do
    [ -z "$(port_pid "$port")" ] && return 0
    sleep 1
  done
  return 1
}

ensure_port_available() {
  local port="$1" kind="$2" pid owner
  pid="$(port_pid "$port")"
  if [ -z "$pid" ]; then
    return 0
  fi
  if [ "$kind" = "backend" ] && is_our_backend "$pid"; then
    echo "[dev] 停止旧后端 pid=$pid (端口 $port)"
    kill "$pid" 2>/dev/null || true
    wait_port_free "$port" 20 || { echo "[dev] 端口 $port 未释放"; exit 1; }
    return 0
  fi
  if [ "$kind" = "frontend" ] && is_our_frontend "$pid"; then
    echo "[dev] 停止旧前端 pid=$pid (端口 $port)"
    kill "$pid" 2>/dev/null || true
    wait_port_free "$port" 20 || { echo "[dev] 端口 $port 未释放"; exit 1; }
    return 0
  fi
  owner="$(ps -p "$pid" -o command= 2>/dev/null | cut -c1-120)"
  echo "[dev] 端口 $port 被其它进程占用: pid=$pid"
  echo "      $owner"
  echo "      请释放端口，或在 .env 中修改 LHJYPT_WEB_PORT（仅前端可改）"
  exit 1
}

# 日志轮转：单文件超过 10MB 归档，保留最近 5 个（P3-002）
rotate_logs() {
  mkdir -p "$ROOT/logs"
  for name in backend frontend; do
    local f="$ROOT/logs/$name.log"
    if [ -f "$f" ] && [ "$(wc -c < "$f" | tr -d ' ')" -gt 10485760 ]; then
      mv "$f" "$ROOT/logs/$name.log.$(date +%Y%m%d-%H%M%S)"
      echo "[dev] 日志轮转: $name.log"
    fi
  done
  ls -1t "$ROOT/logs"/backend.log.* 2>/dev/null | tail -n +6 | xargs rm -f 2>/dev/null || true
  ls -1t "$ROOT/logs"/frontend.log.* 2>/dev/null | tail -n +6 | xargs rm -f 2>/dev/null || true
}

start_middleware() {
  local all_present=1
  local containers=(lhjypt-postgres lhjypt-clickhouse lhjypt-kafka lhjypt-redis lhjypt-zookeeper)
  for c in "${containers[@]}"; do
    docker inspect "$c" >/dev/null 2>&1 || all_present=0
  done
  if [ "$all_present" = 1 ]; then
    echo "[dev] 中间件容器已存在，确保运行（重启后可能处于停止态）..."
    docker start "${containers[@]}" >/dev/null 2>&1 || true
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
  for f in scripts/init-p1.sql scripts/init-p2.sql scripts/migration/*.sql; do
    echo "      <- $f"
    docker exec -i lhjypt-postgres psql -U lhjypt -d lhjypt < "$ROOT/$f" >/dev/null
  done
}

backend_up() {
  rotate_logs
  if curl -s -m 2 "http://localhost:${BACKEND_PORT}/actuator/health" 2>/dev/null | grep -q '"status":"UP"'; then
    if [ "${LHJYPT_REBUILD:-0}" != "1" ]; then
      echo "[dev] 后端已在运行 (${BACKEND_PORT})"
      return 0
    fi
    echo "[dev] 后端已在运行，但请求重建，先停止旧进程"
    local pid
    pid="$(port_pid "$BACKEND_PORT")"
    [ -n "$pid" ] && kill "$pid" 2>/dev/null || true
    wait_port_free "$BACKEND_PORT" 20 || { echo "[dev] 端口 ${BACKEND_PORT} 未释放"; exit 1; }
  fi
  ensure_port_available "$BACKEND_PORT" backend
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
    if curl -s -m 2 "http://localhost:${BACKEND_PORT}/actuator/health" 2>/dev/null | grep -q '"status":"UP"'; then
      echo "[dev] 后端 UP (${BACKEND_PORT})"
      return 0
    fi
    sleep 2
  done
  echo "[dev] 后端启动超时，见 logs/backend.log"
  tail -20 "$ROOT/logs/backend.log" 2>/dev/null || true
  exit 1
}

frontend_up() {
  rotate_logs
  if curl -s -m 2 -o /dev/null -w '%{http_code}' "http://localhost:${WEB_PORT}/" 2>/dev/null | grep -q 200; then
    echo "[dev] 前端已在运行 (${WEB_PORT})"
    return 0
  fi
  ensure_port_available "$WEB_PORT" frontend
  [ -d "$ROOT/web/node_modules" ] || { echo "[dev] 安装前端依赖..."; (cd "$ROOT/web" && npm install); }
  echo "[dev] 启动前端 -> logs/frontend.log"
  (cd "$ROOT/web" && nohup npm run dev > "$ROOT/logs/frontend.log" 2>&1 & echo $! > "$FRONTEND_PID_FILE")
  for i in $(seq 1 30); do
    if curl -s -m 2 -o /dev/null -w '%{http_code}' http://localhost:${WEB_PORT}/ 2>/dev/null | grep -q 200; then
      echo "[dev] 前端 UP (${WEB_PORT}, proxy -> 8081)"
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
  pkill -f "$ROOT/web/node_modules/.bin/vite" 2>/dev/null || true
  echo "[dev] 前后端已停止"
}

status() {
  echo "== 中间件 =="
  docker ps --filter "name=lhjypt-" --format '{{.Names}}\t{{.Status}}' || echo "Docker 未连接"
  echo "== 后端 =="
  echo "port ${BACKEND_PORT}: pid=$(port_pid "$BACKEND_PORT" || true)"
  curl -s -m 2 "http://localhost:${BACKEND_PORT}/actuator/health" || echo "${BACKEND_PORT} 未响应"
  echo
  echo "== 前端 =="
  echo "port ${WEB_PORT}: pid=$(port_pid "$WEB_PORT" || true)"
  curl -s -m 2 -o /dev/null -w 'http=%{http_code}\n' "http://localhost:${WEB_PORT}/" || echo "${WEB_PORT} 未响应"
}

case "${1:-up}" in
  up|start) start_middleware; apply_sql; backend_up; frontend_up; status;;
  status)   status;;
  restart)  pkill -f 'core-0.1.0-SNAPSHOT.jar' 2>/dev/null || true
            pkill -f "$ROOT/web/node_modules/.bin/vite" 2>/dev/null || true
            rm -f "$BACKEND_PID_FILE" "$FRONTEND_PID_FILE"
            wait_port_free "$BACKEND_PORT" 20 || true
            wait_port_free "$WEB_PORT" 20 || true
            backend_up; frontend_up; status;;
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
  *) echo "用法: $0 {up|start|status|restart|down|reset-db|logs [backend|frontend|<container>]}"; exit 1;;
esac