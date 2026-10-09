# 行情可靠性：多源故障切换、超时重试与数据质量（P3-006）

## 1. 多源与故障切换

| 源 | 说明 | 实测 |
|----|------|------|
| `gateio` | 主源（`api.gateio.ws`，K 线字段 t/amount/O/H/L/C/V） | 可用但**慢（~16s）** |
| `binance-vision` | 备用（`data-api.binance.vision`，公开免密钥） | 可用且**快（~0.6s）** |

- 顺序由 `lhjypt.market.real.sources` 决定（默认 `gateio,binance-vision`），按序尝试，**首个成功即用**；全部失败抛出并携带各源错误摘要。
- 每源重试 `max-attempts`（默认 2）次，间隔 `retry-backoff-ms`（默认 800ms）。
- 超时 `timeout-seconds`（默认 **25s**）：**这是此前刷新失败的根因**——Gate 响应 16s+ 超过旧默认 10s。
- `GET /api/market/real/status` 返回 `currentSource` 与各源 `sourceState`（status/lastError/lastSuccessAt）。
- 所有动作落 `lhjypt_market_refresh_total{result=ok|error}` 指标。

## 2. 数据质量检测

```bash
curl "http://localhost:8081/api/market/real/quality?currencyPair=BTC_USDT&frequency=1h&limit=1000" \
  -H 'X-User-Role: TRADER' -H 'X-User-Id: trader-1'
```

检测项：
- **缺口**：相邻 bar 时间差 > 1 个周期（计算缺失根数，最多列出 20 条样本）；
- **异常值**：`high<low`、`open/close` 超出 `[low,high]`、`close<=0`、`volume<=0`、空值；
- **重复**：相同 `bar_time`。

返回 `total / gapCount / gaps / anomalyCount / anomalies / duplicates / earliest / latest`。

## 3. 回补（更长历史）

单次拉取上限 1000 根（`limit`）；补充更长历史时以较大 `limit` 调用 `POST /api/market/real/refresh`（去重保证可重复执行）。
如需数年数据，建议按周期分批（例：先用 `limit=1000` 拉最近，再逐步扩大时间窗）——分页翻页回补为后续可选增强。

## 4. 配置速查

| 配置 | 默认 | 说明 |
|------|------|------|
| `LHJYPT_MARKET_REAL_SOURCES` | `gateio,binance-vision` | 源优先级 |
| `LHJYPT_MARKET_REAL_TIMEOUT` | `25` | HTTP 超时（秒） |
| `LHJYPT_MARKET_REAL_ATTEMPTS` | `2` | 每源重试次数 |
| `LHJYPT_MARKET_REAL_BACKOFF` | `800` | 重试退避（ms） |
| `LHJYPT_MARKET_REAL_URL` | Gate 地址 | 主源地址（可指向不可达以演练 failover） |

## 5. 演练记录

- 默认配置：刷新成功（inserted 53），`currentSource=gateio`，质量 0 缺口/0 异常（300 根）。
- 主源不可达（`LHJYPT_MARKET_REAL_URL=http://127.0.0.1:9/dead`）：**自动切 `binance-vision`**（inserted 37），`sourceState` 显示 gateio ERROR / binance-vision OK。