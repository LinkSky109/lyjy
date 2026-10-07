# 真实行情数据源（Gate.io 现货 K 线）

> OBS-005：把 Mock 行情补上真实数据源。策略回测与 VaR 等消费 `/api/market/bars` 的模块
> 在刷新后可查真实 K 线。无需 API 密钥，国内可直达。

## 源与格式

- 源：Gate.io 公开 REST（`GET /api/v4/spot/candlesticks`），免密钥。
- 返回：每根 `[ts(秒), amount, open, high, low, close, volume, finished]`。
- 交易对：Gate 格式 `BTC_USDT`；周期 `1m/5m/15m/1h/1d`。

## 使用

```bash
# 1) 查看源状态
curl http://localhost:8081/api/market/real/status -H 'X-User-Role: TRADER' -H 'X-User-Id: trader-1'

# 2) 拉取真实 K 线写入 ClickHouse（写操作需 ADMIN）
curl -X POST "http://localhost:8081/api/market/real/refresh?currencyPair=BTC_USDT&interval=1h&limit=200" \
  -H 'Content-Type: application/json' -H 'X-User-Role: ADMIN' -H 'X-User-Id: admin'
# -> data = 新增条数（按 symbol+频率+bar_time 去重，重复拉取返回 0）

# 3) 查询真实行情（/api/market/bars 需要时间窗参数）
curl "http://localhost:8081/api/market/bars?symbol=BTC_USDT&frequency=1h&startTime=2026-10-06T00:00:00&endTime=2026-10-08T23:59:59&limit=10" \
  -H 'X-User-Role: TRADER' -H 'X-User-Id: trader-1'
```

## 配置

`core/src/main/resources/application.yaml` → `lhjypt.market.real.*`（或环境变量）：

| 变量 | 默认 | 说明 |
|------|------|------|
| `LHJYPT_MARKET_REAL` | `true` | 启用真实行情 REST |
| `LHJYPT_MARKET_REAL_URL` | `https://api.gateio.ws/api/v4/spot/candlesticks` | 源地址 |
| `LHJYPT_MARKET_REAL_PAIR` | `BTC_USDT` | 默认交易对 |
| `LHJYPT_MARKET_REAL_INTERVAL` | `1h` | 默认周期 |
| `LHJYPT_MARKET_REAL_LIMIT` | `200` | 默认拉取条数（单次 ≤1000） |
| `LHJYPT_MARKET_REAL_TIMEOUT` | `10` | HTTP 超时（秒） |
| `LHJYPT_MARKET_REAL_REFRESH_MS` | `900000` | 定时自动刷新间隔（ms），`0` 关闭 |

## 定时自动刷新

默认每 15 分钟自动拉取一次默认交易对 K 线（`@Scheduled` + 分布式锁防重，失败静默等待下轮）；`status` 返回 `autoRefreshMs` 与最近刷新时间。

## 基于真实行情的回测

```bash
# 前提：先刷新真实行情（写入 ClickHouse bar_data）
curl -X POST "http://localhost:8081/api/market/real/refresh?currencyPair=BTC_USDT&interval=1h&limit=1000" \
  -H 'X-User-Role: ADMIN' -H 'X-User-Id: admin'

# 真实行情回测（数据窗取 body 时间；频率默认 1h，可 5m/15m/1d）
curl -X POST "http://localhost:8081/api/backtest/run-real?frequency=1h&mode=close" \
  -H 'Content-Type: application/json' -H 'X-User-Role: TRADER' -H 'X-User-Id: trader-1' \
  -d '{"strategyName":"BUILTIN_SMA","symbols":["BTC_USDT"],"startTime":"2026-10-01T00:00:00","endTime":"2026-10-08T23:59:59","initialCapital":1000000}'
# -> COMPLETED，指标与交易明细基于真实 K 线；mode=ohlc（默认，bar 内 OHLC 参考 tick）
#    或 mode=close（收盘信号，每 bar 1 tick，贴近收盘价信号策略）

# 前端「回测」页提供「真实行情回测」按钮 + 「收盘信号」勾选
```

无真实数据时返回 422 提示先执行 refresh。

## SDK 自写策略 × 真实行情 回测（OBS-008）

后端内置策略之外，可在 **Python SDK 里用自写策略**跑真实行情回测
（`lhjypt.backtest`：`load_real_bars` 拉平台真实 K 线 + `SimpleMatchingEngine` 收盘撮合）：

```bash
# 示例：真实 BTC_USDT 1h，窗口 7 天，复用双均线策略
cd strategy-sdk
PYTHONPATH=. python lhjypt/examples/real_backtest_demo.py BTC_USDT 1h 7
# -> 拉取 159 根真实 K 线，策略信号 8 笔成交，输出净值/收益率

import lhjypt.backtest.real_data as rd
from lhjypt.backtest.simple_matching import SimpleMatchingEngine
```

- `load_real_bars(RestClient, symbol, freq, start, end)`：从 `/api/market/bars` 拉真实数据（需已 `refresh`）。
- `SimpleMatchingEngine`：`set_price` 后策略 `buy/sell` 按收盘价撮合、记账现金/持仓/手续费，`summary()` 出净值。
- 策略文件照常继承 `lhjypt.Strategy`（如 `examples/dual_ma_strategy.py`）。

## 数据流

```
Gate.io REST → GateioKlineSource(HTTP) → RealMarketDataService(去重) 
    → ClickHouse lhjypt.bar_data → /api/market/bars、VaR(ClickHouse 行情) 等消费方
```

刷新为**手动触发**（`POST refresh`）；如需自动，可加定时任务（当前单人使用手动足够）。

## 边界

- 时间戳按服务端时区本地化（Asia/Shanghai +8）写入 `bar_time`。
- 与 Mock 行情并存：Mock 提供 tick 推送，Gate 提供真实历史/实时 K 线，二者互不冲突。