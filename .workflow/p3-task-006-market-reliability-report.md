# P3-006 行情回补与数据质量（含源重试/超时/备用源） · 测试报告

> 分支：feature/p3-006-market-reliability · 日期：2026-10-09 · 状态：VERIFIED

## 1. 交付内容
| 产物 | 说明 |
|------|------|
| `MarketDataSource` SPI + `MarketCandle` | 多源抽象（标准 pair 入参，内部转换） |
| `GateioMarketDataSource` / `BinanceVisionMarketDataSource` | 主源（Gate）+ 备用源（Binance 公开数据，免密钥且快） |
| `RealMarketDataService` 改造 | **多源故障切换**（按 `sources` 顺序，首个成功即用）+ **每源重试/退避** + 超时可配；`status` 增 `currentSource/sourceState/timeoutSeconds` |
| `MarketDataQualityService` | 缺口（>1 周期）、异常值（OHLC 越界/非正量价/空值）、重复时间戳检测 + API `GET /api/market/real/quality` |
| 配置 | `sources` / `timeout-seconds(默认 25s)` / `max-attempts` / `retry-backoff-ms` |
| `docs/market-reliability.md` | 多源、质量检测、回补、配置、演练记录 |

## 2. 实测
### 根因修复（关键）
- **Gate 响应 ~16.6s 超过旧默认 10s 超时** → 刷新失败。超时提升至 25s 后：默认配置刷新成功 **inserted=53**，`currentSource=gateio`、gateio OK。

### 真实故障切换
- 主源指向不可达（`LHJYPT_MARKET_REAL_URL=http://127.0.0.1:9/dead`）：
  `inserted=37`、**`currentSource=binance-vision`**、`sourceState` = binance-vision OK / gateio ERROR。

### 数据质量
- BTC_USDT@1h 300 根：`gapCount=0 anomalyCount=0 duplicates=0`，范围 09-27→10-09。

## 3. 过程中的修复
- 源状态内层 `ConcurrentHashMap` 不接受 null 值（`lastError=null` NPE）→ 空串替代。
- 多源改造后单测适配（假源可通过性/失败可编程）。

## 4. 质量门禁
| 项 | 结果 |
|----|------|
| `RealMarketDataServiceTest` | 5/5（failover / 去重 / 全失败抛错 / 单源重试 / 禁用与调度守卫） |
| `MarketDataQualityServiceTest` | 4/4（缺口+重复 / 异常 / 空数据 / 周期映射） |
| 全量 `mvn verify` | **294/294**（0 跳过，JaCoCo 通过） |
| 启动/切换演练 | 通过 |

## 5. 结论
行情接入具备超时/重试/多源切换与数据质量检测（P3-006 完成，含用户要求的重试/超时/备用源）。