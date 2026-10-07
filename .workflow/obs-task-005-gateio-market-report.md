# OBS-005 真实行情数据源接入 · 测试报告

> 分支：feature/obs-005-gateio-market · 日期：2026-10-07 · 状态：REAL_DATA_VERIFIED

## 1. 交付内容
| 产物 | 说明 |
|------|------|
| `GateioKlineSource` | Gate.io 现货 K 线 HTTP 拉取 + 解析（公开 API 免密钥，国内可达） |
| `RealMarketDataService` | 拉取→按 (symbol, freq, bar_time) 去重→写 ClickHouse `bar_data` |
| `RealMarketController` | `POST /api/market/real/refresh`（ADMIN）· `GET /api/market/real/status` |
| `RealMarketProperties` | `lhjypt.market.real.*` 配置（enabled/baseUrl/pair/interval/limit/timeout） |
| `docs/market-real.md` | 使用指南、配置表、数据流 |

## 2. 实测（真实数据 E2E）
| 场景 | 结果 |
|------|------|
| 源连通性 | Gate.io 可达；Binance/OKX/Coinbase 不可达（本机网络）→ 选型 Gate.io |
| refresh BTC_USDT 1h | 新增 **100 根真实 K 线**（ClickHouse bar_data 确认） |
| 重复 refresh | **新增=0**（时间戳去重） |
| /api/market/bars 真实数据 | 5 根（10/07 08:00–12:00，C 85557.8→84155，与实时一致） |
| /api/market/real/status | 正常（修复 Map.of null 值 NPE → LinkedHashMap） |

## 3. 质量门禁
| 项 | 结果 |
|----|------|
| 单元测试 | `GateioKlineSourceTest` 2（解析/非法抛错）+ `RealMarketDataServiceTest` 3（映射去重/禁用拒绝/默认兜底） |
| `mvn verify` | **258/258**（0 跳过，JaCoCo 通过） |
| 修复项 | status() 用 `Map.of` 传 null 值 NPE → 改 LinkedHashMap |

## 4. 说明
- Mock 行情（tick 推送）与真实行情（K 线）并存；真实 bar 写入后 `/api/market/bars` 与 VaR 行情消费即基于真实数据。
- 刷新为手动触发（单人使用足够）；定时刷新/回测按 bar 扩展留待后续。
- 演练中 PG 连接打满（遗留进程 Hikari 连接）→ 重启 PG + `dev.sh up` 恢复（与历史一致）。

## 5. 结论
真实行情数据源端到端接入成功（拉取→入库→查询→去重），平台行情从 Mock 升级为真实数据可查。