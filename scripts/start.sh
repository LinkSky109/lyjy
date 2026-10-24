#!/usr/bin/env bash
# 一键启动：中间件 + 增量 SQL + 后端 8081 + 前端 5180
# 首次使用：cp .env.example .env（按本机端口修改）
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
JAR="$ROOT/core/target/core-0.1.0-SNAPSHOT.jar"

# 源码或 pom 比 jar 新时自动重建，避免一键启动跑到旧包
if [ -f "$JAR" ] && find "$ROOT/core/src" "$ROOT/core/pom.xml" "$ROOT/pom.xml" \
  -newer "$JAR" -print -quit 2>/dev/null | grep -q .; then
  echo "[start] 检测到后端源码更新，自动重建 jar"
  export LHJYPT_REBUILD=1
fi

exec "$ROOT/scripts/dev.sh" start
