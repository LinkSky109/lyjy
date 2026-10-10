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

## 3. 回补（更长历史，P4-001 分页翻页）

单次拉取上限 1000 根；`POST /api/market/real/refresh` 适合拉取最近一段。
需要**更长历史**时用分页回补接口，以最早一根 K 线时间为游标向前翻页，直到达到目标条数 / 翻完单页 / 无新增：

```bash
# 回补 ~3000 根 1h K 线（pageSize=1000 × 3 页）；写操作需 ADMIN
curl -X POST "http://localhost:8081/api/market/real/backfill?currencyPair=BTC_USDT&interval=1h&total=3000&pageSize=1000&source=binance-vision" \
  -H 'X-User-Role: ADMIN' -H 'X-User-Id: admin'
# -> { symbol, frequency, pages, fetched, inserted, source, earliest, latest }

# 指定 source 只走该源（失败不切换）；省略则按 sources 顺序故障切换并跨页粘性复用首个成功源
```

| 参数 | 默认 | 说明 |
|------|------|------|
| `total` | `pageSize × backfill-max-pages` | 目标条数（上限同） |
| `pageSize` | `backfill-page-size`(1000) | 每页条数（上限 1000） |
| `source` | 空（按顺序故障切换） | 指定数据源；空则 gateio→binance-vision |

要点：
- 支持分页的源在请求 URL 附带游标（Gate `to`=秒 / Binance `endTime`=毫秒）；不支持的源退化为单页。
- 跨页防死循环：游标无法继续前移或本页无新增即停。
- 按 `(symbol,frequency,bar_time)` 全窗去重，**可重复执行**（再次回补 `inserted=0`）。

演练记录（`BTC_USDT@1h`，`source=binance-vision`）：327 根 → 回补 3 页 fetched 3000 / inserted 2673 → 3000 根（2026-06-07 ~ 2026-10-10），0 缺口/0 异常/0 重复；重复回补 inserted 0。

## 4. 配置速查

| 配置 | 默认 | 说明 |
|------|------|------|
| `LHJYPT_MARKET_REAL_SOURCES` | `gateio,binance-vision` | 源优先级 |
| `LHJYPT_MARKET_REAL_TIMEOUT` | `25` | HTTP 超时（秒） |
| `LHJYPT_MARKET_REAL_ATTEMPTS` | `2` | 每源重试次数 |
| `LHJYPT_MARKET_REAL_BACKOFF` | `800` | 重试退避（ms） |
| `LHJYPT_MARKET_REAL_URL` | Gate 地址 | 主源地址（可指向不可达以演练 failover） |
| `LHJYPT_MARKET_REAL_BACKFILL_PAGE` | `1000` | 分页回补每页条数（上限 1000） |
| `LHJYPT_MARKET_REAL_BACKFILL_PAGES` | `10` | 分页回补最大页数（总历史上限） |

## 5. 演练记录

- 默认配置：刷新成功（inserted 53），`currentSource=gateio`，质量 0 缺口/0 异常（300 根）。
- 主源不可达（`LHJYPT_MARKET_REAL_URL=http://127.0.0.1:9/dead`）：**自动切 `binance-vision`**（inserted 37），`sourceState` 显示 gateio ERROR / binance-vision OK。