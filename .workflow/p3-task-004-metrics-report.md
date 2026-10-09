# P3-004 Prometheus 指标与看板 · 测试报告

> 分支：feature/p3-004-metrics · 日期：2026-10-09 · 状态：VERIFIED

## 1. 交付内容
| 产物 | 说明 |
|------|------|
| `micrometer-registry-prometheus` | `core/pom.xml` 依赖；`management.metrics.export.prometheus.enabled=true`；暴露 include 增 `prometheus` |
| `MetricsService`（monitor） | 业务埋点封装：`orderSubmitted` / `backtestRun` / `marketRefresh(inserted)` / **`marketRefreshFailure`** / `circuitTrip(kind)` / `capacityLoad(pct)`（gauge） |
| 埋点接入 | `BacktestEngine`（回测 runs+duration Timer）、`RealMarketDataService`（刷新成功/失败）、`StrategyCircuitBreakerService`/`PortfolioCircuitService`（熔断 trips）——均用「主构造 + 便捷构造」避免改动既有单测 |
| `docs/metrics.md` | 端点、指标清单、PromQL、Prometheus scrape/Grafana 接入、埋点扩展指引 |

## 2. 实测
| 项 | 结果 |
|----|------|
| `/actuator/prometheus` | 可用，**214 指标行**；含 JVM、`hikaricp_connections_active`（2 数据源）、`http_server_requests_*`、`application="lhjypt-core"` 标签 |
| 自定义 gauge | `lhjypt_capacity_load_pct` 出现 |
| 业务计数（回测） | `lhjypt_backtest_runs_total{status="COMPLETED"} 1` + `lhjypt_backtest_duration_seconds` |
| 业务计数（行情失败） | `lhjypt_market_refresh_total{result="error"} 2`（网络超时路径也纳入） |

## 3. 过程中的缺陷修复（重要）
1. **YAML 重复键 + 历史禁用**：`management.metrics` 重复且既有块 `export.prometheus.enabled=false`（P1 遗留显式禁用）→ 合并并置 `true`——这是此前无 prometheus 端点的根因。
2. **双构造 Bean 缺 `@Autowired`** 致 Spring 启动失败（`No default constructor found`）→ 4 个服务主构造补 `@Autowired`（与 P1 加固同类教训，已写入文档）。
3. 埋点盲区：刷新失败未计数 → 新增 `marketRefreshFailure()`（`result="error"`）。

## 4. 质量门禁
| 项 | 结果 |
|----|------|
| 受影响单测（回测/行情/两熔断） | 全绿（便捷构造零改动） |
| 全量 `mvn verify` | **288/288**（0 跳过，JaCoCo 通过） |
| 启动验证 | 后端 UP；prometheus 端点可抓取 |

## 5. 说明
- 本机观测已可用（cURL/脚本）；接入 Prometheus/Grafana 为可选（配置示例见文档）。
- 单机单人不需要独立看板服务；`metrics.md` 给出按需接入路径。

## 6. 结论
指标已暴露且业务埋点生效（含失败计数），可观测性基础就绪（P3-004 完成）。