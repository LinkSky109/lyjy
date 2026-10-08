#!/usr/bin/env bash
# ============================================================================
# lhjypt 数据备份与恢复（P3-001）
#
#   ./scripts/backup.sh backup              备份 PG + ClickHouse(关键表) + 配置
#   ./scripts/backup.sh list                列出备份归档
#   ./scripts/backup.sh verify <archive>    校验归档（sha256）
#   ./scripts/backup.sh restore <archive>   恢复（先自动停后端，恢复后手动 up）
#
# 归档：backups/lhjypt-backup-<ts>.tar.gz（含 pg.sql.gz / ch-*.native.gz /
#       config.tar.gz / manifest.txt）+ 同名 .sha256
# ============================================================================
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BACKUP_DIR="$ROOT/backups"
CH_TABLES=(bar_data tick_data metric_sample)

if [ -n "${LHJYPT_DOCKER_HOST:-}" ]; then
  export DOCKER_HOST="$LHJYPT_DOCKER_HOST"; export DOCKER_API_VERSION=1.44
elif [ -S "$HOME/.lima/tuna-docker/sock/docker.sock" ]; then
  export DOCKER_HOST="unix://$HOME/.lima/tuna-docker/sock/docker.sock"; export DOCKER_API_VERSION=1.44
fi

sha256_of() { shasum -a 256 "$1" | awk '{print $1}'; }

do_backup() {
  mkdir -p "$BACKUP_DIR"
  local ts archive work
  ts="$(date +%Y%m%d-%H%M%S)"
  archive="$BACKUP_DIR/lhjypt-backup-$ts.tar.gz"
  work="$(mktemp -d)"
  trap 'rm -rf "${work:-}"' EXIT

  echo "[backup] PostgreSQL 逻辑备份..."
  docker exec lhjypt-postgres pg_dump -U lhjypt -d lhjypt | gzip > "$work/pg.sql.gz"

  echo "[backup] ClickHouse 关键表导出（Native，可回灌）..."
  for t in "${CH_TABLES[@]}"; do
    docker exec lhjypt-clickhouse clickhouse-client \
      --query "SELECT * FROM lhjypt.$t FORMAT Native" | gzip > "$work/ch-$t.native.gz"
  done

  echo "[backup] 配置与脚本打包（排除密钥文件）..."
  (cd "$ROOT" && tar czf "$work/config.tar.gz" \
      --exclude='config/key-store.properties' --exclude='.env' \
      config scripts core/src/main/resources/application.yaml docs/quickstart.md 2>/dev/null || true)

  {
    echo "lhjypt backup"
    echo "time: $(date '+%Y-%m-%d %H:%M:%S')"
    echo "pg_rows_strategy: $(docker exec lhjypt-postgres psql -U lhjypt -d lhjypt -t -A -c 'SELECT count(*) FROM strategy_info;' 2>/dev/null || echo 0)"
    echo "pg_rows_orders: $(docker exec lhjypt-postgres psql -U lhjypt -d lhjypt -t -A -c 'SELECT count(*) FROM orders;' 2>/dev/null || echo 0)"
    for t in "${CH_TABLES[@]}"; do
      echo "ch_rows_${t}: $(docker exec lhjypt-clickhouse clickhouse-client --query "SELECT count() FROM lhjypt.$t;" 2>/dev/null || echo 0)"
    done
    echo "--- files ---"
    for f in "$work"/*; do
      echo "$(basename "$f") $(sha256_of "$f")"
    done
  } > "$work/manifest.txt"

  tar czf "$archive" -C "$work" .
  sha256_of "$archive" > "$archive.sha256"
  echo "[backup] 完成: $archive ($(du -h "$archive" | awk '{print $1}'))"
  echo "[backup] 校验: $(cat "$archive.sha256")"
}

do_verify() {
  local archive="${1:?用法: backup.sh verify <archive>}"
  [ -f "$archive" ] || { echo "归档不存在: $archive"; exit 1; }
  local expect actual
  expect="$(cat "$archive.sha256" 2>/dev/null | awk '{print $1}')"
  actual="$(sha256_of "$archive")"
  if [ -n "$expect" ] && [ "$expect" = "$actual" ]; then
    echo "[verify] OK: $archive"
  else
    echo "[verify] 校验失败（期望 $expect 实际 $actual）"; exit 1
  fi
}

do_restore() {
  local archive="${1:?用法: backup.sh restore <archive>}"
  do_verify "$archive"
  echo "[restore] 停止后端（PG 恢复需独占连接）..."
  pkill -f 'core-0.1.0-SNAPSHOT.jar' 2>/dev/null || true
  sleep 2

  local work; work="$(mktemp -d)"
  trap 'rm -rf "${work:-}"' EXIT
  tar xzf "$archive" -C "$work"

  echo "[restore] 重建 public schema 并导入 PostgreSQL..."
  docker exec lhjypt-postgres psql -U lhjypt -d lhjypt -q \
    -c "DROP SCHEMA public CASCADE; CREATE SCHEMA public;"
  gunzip -c "$work/pg.sql.gz" | docker exec -i lhjypt-postgres psql -U lhjypt -d lhjypt -q

  echo "[restore] 回灌 ClickHouse 关键表..."
  for t in "${CH_TABLES[@]}"; do
    gunzip -c "$work/ch-$t.native.gz" > "$work/ch-$t.native"
    if [ ! -s "$work/ch-$t.native" ]; then
      echo "  跳过空表 $t"
      continue
    fi
    docker exec -i lhjypt-clickhouse clickhouse-client \
      --query "TRUNCATE TABLE lhjypt.$t" >/dev/null
    docker exec -i lhjypt-clickhouse clickhouse-client \
      --query "INSERT INTO lhjypt.$t FORMAT Native" < "$work/ch-$t.native"
    echo "  已回灌 $t"
  done

  echo "[restore] 完成。请执行 ./scripts/dev.sh up 重启后端。"
  grep -E 'rows_' "$work/manifest.txt" 2>/dev/null || true
}

do_list() {
  mkdir -p "$BACKUP_DIR"
  ls -lh "$BACKUP_DIR"/lhjypt-backup-*.tar.gz 2>/dev/null || echo "暂无备份"
}

case "${1:-backup}" in
  backup)  do_backup;;
  verify)  do_verify "${2:-}";;
  restore) do_restore "${2:-}";;
  list)    do_list;;
  *) echo "用法: $0 {backup|list|verify <archive>|restore <archive>}"; exit 1;;
esac