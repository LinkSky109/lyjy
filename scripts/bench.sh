#!/usr/bin/env bash
# ============================================================================
# lhjypt 关键路径压测基线（P3-007）
#
#   ./scripts/bench.sh [fast_requests] [fast_concurrency]
#   默认 fast=60 并发 6（回测为 CPU 密集，固定 10 次/并发 2）
#
# 输出：Markdown 表格（延迟 p50/p95/p99/max、成功率、吞吐），可重定向到文档
# ============================================================================
set -euo pipefail
FAST_REQ="${1:-60}"
FAST_CONC="${2:-6}"

python3 - "$FAST_REQ" "$FAST_CONC" <<'PY'
import json
import statistics
import sys
import time
import urllib.error
import urllib.request
from concurrent.futures import ThreadPoolExecutor

BASE = "http://localhost:8081"
HEADERS = {"X-User-Role": "TRADER", "X-User-Id": "trader-1",
           "Content-Type": "application/json"}

FAST_REQ = int(sys.argv[1])
FAST_CONC = int(sys.argv[2])

ENDPOINTS = [
    ("health", "GET", "/actuator/health", None, FAST_REQ, FAST_CONC),
    ("market.bars", "GET",
     "/api/market/bars?symbol=BTC_USDT&frequency=1h&startTime=2026-09-01T00:00:00"
     "&endTime=2026-10-10T00:00:00&limit=200", None, FAST_REQ, FAST_CONC),
    ("trade.positions", "GET",
     "/api/trade/platform/positions?accountId=DEFAULT-001", None, FAST_REQ, FAST_CONC),
    ("strategy.risk.overview", "GET", "/api/strategy/risk/overview", None, FAST_REQ, FAST_CONC),
    ("backtest.run", "POST", "/api/backtest/run",
     {"symbols": ["BTCUSDT"]}, 10, 2),
]


def one(method, path, body):
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(BASE + path, data=data, headers=HEADERS, method=method)
    t0 = time.perf_counter()
    try:
        with urllib.request.urlopen(req, timeout=120) as resp:
            resp.read()
            return (time.perf_counter() - t0) * 1000, resp.status
    except urllib.error.HTTPError as e:
        return (time.perf_counter() - t0) * 1000, e.code
    except Exception:
        return (time.perf_counter() - t0) * 1000, 0


def percentile(values, p):
    if not values:
        return 0.0
    values = sorted(values)
    idx = min(len(values) - 1, int(round((p / 100) * (len(values) - 1))))
    return values[idx]


print("| 端点 | 请求 | 并发 | 成功率 | p50(ms) | p95(ms) | p99(ms) | max(ms) | 吞吐(rps) |")
print("|------|------|------|--------|---------|---------|---------|---------|-----------|")
for name, method, path, body, reqs, conc in ENDPOINTS:
    lat = []
    ok = 0
    start = time.perf_counter()
    with ThreadPoolExecutor(max_workers=conc) as pool:
        for ms, status in pool.map(lambda _: one(method, path, body), range(reqs)):
            lat.append(ms)
            if 200 <= status < 300:
                ok += 1
    elapsed = time.perf_counter() - start
    rps = reqs / elapsed if elapsed > 0 else 0
    print("| %s | %d | %d | %.0f%% | %.1f | %.1f | %.1f | %.1f | %.1f |" % (
        name, reqs, conc, 100.0 * ok / reqs,
        percentile(lat, 50), percentile(lat, 95), percentile(lat, 99),
        max(lat), rps))
PY