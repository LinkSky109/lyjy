# P4 一致性校验报告

> 批次：P4 后续加固（P4-001~003） · 巡检日期：2026-10-10 · 最终状态：CONSISTENCY_PASS

## 1. 五维结果

| 维度 | 结果 | 说明 |
|------|------|------|
| 代码一致性 | 通过 | `mvn verify` 304/0/1（1 跳过为环境限制）+ SDK pytest 29/29；工作区干净 |
| 环境一致性 | 通过 | 5 中间件 healthy、后端 8081 UP（db/CH/redis）、前端 5173 200、观测栈 up |
| 数据一致性 | 通过 | PG 关键表齐全、CH bar_data 3001 根 0 重复、质量 0 缺口/0 异常 |
| 安全一致性 | 通过 | `/backfill` 仅 ADMIN（TRADER 403）；Grafana 禁注册；归档/配置不含密钥 |
| 文档一致性 | 通过 | p4-plan + 3 份任务报告 + observability/deploy/metrics/README 对齐 |

## 2. 代码一致性

| 检查项 | 结果 |
|--------|------|
| 全量 `mvn verify` | 304 通过 / 0 失败 / 1 跳过（JaCoCo check 通过，BUILD SUCCESS） |
| Python SDK | 29/29 通过 |
| 新增 | `RealMarketDataServiceTest`+7、`GateioKlineSourceTest`+1、`BinanceVisionMarketDataSourceTest`+2（源包新增） |
| 端点 | 31 控制器 / 176 映射；新增 `POST /api/market/real/backfill` |
| 分支/提交 | `f8bb3ea`（P4-001）、`74df8cf`（P4-002/003）；工作区干净 |

## 3. 环境一致性

| 组件 | 实测 | 结果 |
|------|------|------|
| PostgreSQL/ClickHouse/Kafka/Redis/ZK | 5 容器 healthy（ZK 运行中无 healthcheck） | 通过 |
| 后端 | 8081 `/actuator/health` UP（primaryDataSource/clickHouse UP） | 通过 |
| 前端 | 5173 http 200（Vite） | 通过 |
| 观测栈 | `lhjypt-prometheus`(9090)/`lhjypt-grafana`(3000) up；抓取目标 `lhjypt` up=1 | 通过 |

> 说明：巡检时 8081 运行的是同平台兄弟检出（P3 基线）进程；本批次代码另以 8082 实例验证（`/backfill` TRADER 403 / ADMIN 200）并已跑通真实 E2E。二者为同一仓库不同提交，互不冲突。

## 4. 数据一致性

| 检查项 | 结果 |
|--------|------|
| PG 表 | public 49 表；`strategy_info/orders/fund_account/tenant/trade_record/risk_budget` 均在 |
| CH 表 | `bar_data/tick_data/market_subscription/metric_sample` |
| bar_data | `BTC_USDT@1h` 3001 行，`uniqExact(bar_time)`=3001（0 重复），区间 2026-06-07 ~ 2026-10-10 |
| 质量 | 0 缺口 / 0 异常 / 0 重复（`/api/market/real/quality`） |
| 回补正确性 | 327 → 3 页 fetched3000/inserted2673 → 3000；重复回补 inserted=0 |

## 5. 安全一致性

| 检查项 | 结果 |
|--------|------|
| 回补写权限 | `/api/market/real/backfill` 限 ADMIN（TRADER 403、ADMIN 200，AuthInterceptor） |
| 观测栈 | Grafana `GF_USERS_ALLOW_SIGN_UP=false`；默认口令仅本地（文档提示对外需改密） |
| 密钥 | 密钥接口仍 ADMIN-only（P3-005）；归档/配置未含密钥 |
| 审计 | 敏感写操作经 AuthInterceptor 审计（P2/P3 基线） |

## 6. 文档一致性

| 文档 | 状态 |
|------|------|
| `.workflow/p4-plan.md` | P4-001/002/003 完成；P4-004 候选 |
| `.workflow/p4-task-001~003-*-report.md` | 全部生成 |
| `docs/observability.md`（新增）· `docs/deploy.md`·`metrics.md`·`market-real.md`·`market-reliability.md` | 与本批次对齐 |
| `README.md` | 状态表 + 一键入口（obs.sh）对齐 |

## 7. 已知项（不阻断交付）

| 项 | 影响 | 建议 |
|----|------|------|
| `SchemaMigrationIntegrationTest` 跳过 | 本机 Testcontainers 客户端 API 1.32 vs Lima 守护 ≥1.44；属环境限制，本批次无 schema 变更 | 如需，升级 Testcontainers/docker-java 或降守护 API 下限 |
| bar_time 存量为本地墙钟（epoch 承载本地值） | 仅口径语义，功能一致；巡检已按同口径修正 | 未来多时区协同再评估迁移 UTC |
| 回补逐行写入 | 3000 行约 16s | 大批量可改批量 insert |

## 8. 结论

- 代码、环境、数据、安全、文档五维全部通过；无阻断项与未闭环问题。
- 状态流转 `CONSISTENCY_PASS`；P4 后续加固（P4-001~003）可交付。
