# 性能与容量基线（P3-007）

> 环境：本机（macOS + Lima Docker，5 中间件容器，Java 17，单实例）。用途：单人自用的性能下限与容量可预期性基线。

## 1. 关键路径压测（`./scripts/bench.sh 60 6`）

| 端点 | 请求 | 并发 | 成功率 | p50(ms) | p95(ms) | p99(ms) | max(ms) | 吞吐(rps) |
|------|------|------|--------|---------|---------|---------|---------|-----------|
| health | 60 | 6 | 100% | 9.6 | 43.7 | 45.6 | 46.2 | 395 |
| market.bars（真实 K 线查询） | 60 | 6 | 100% | 16.7 | 47.6 | 47.8 | 48.2 | 303 |
| trade.positions | 60 | 6 | 100% | 5.9 | 10.3 | 10.4 | 10.5 | 908 |
| strategy.risk.overview | 60 | 6 | 100% | 8.0 | 14.4 | 14.8 | 15.1 | 689 |
| backtest.run（1000 tick） | 10 | 2 | 100% | 19.9 | 55.4 | 55.4 | 55.4 | 78 |

> 首轮（调优前）同口径：backtest p50 21.6 / positions p50 6.1 / risk.overview p50 7.8 —— 调优后持平或更优，全部 100% 成功。
> 说明：`health` 需探活 DB+CH+Redis 故 p95 略高（~44ms）；回测为 CPU 密集，单并发 p50 ~20ms（scale 缺陷修复后性能提升显著）。

## 2. 容量与连接池（调优后）

| 指标 | 值 |
|------|----|
| PG 池（`lhjypt-pg`） | max 20 / min-idle 5 / conn-timeout 30s |
| CH 池（`lhjypt-ch`） | max 10 / min-idle 2 / conn-timeout 30s |
| 池 active / pending / timeout_total | 0 / 0 / 0（压测后） |
| Heap（Old Gen） | ~46MB；Young GC 少量（G1） |
| Tomcat 线程 | 默认 200（远超单人负载） |

### 调优项（P3-007）
- **连接池显式化**：`spring.datasource.primary|clickhouse` 下直接绑定 Hikari 参数（pool-name/max/min/conn-timeout/idle/max-lifetime/validation），替代此前依赖驱动默认值——可预期、可观测（`hikaricp_connections_max{pool=...}`）。
- 超时：PG/CH 连接超时 30s；`max-lifetime 30min` 防长连接退化。
- 行情超时（关联 P3-006）：Gate 接入 25s（实测响应 16s+）。

## 3. 结论
- 单人负载下各关键路径 p95 < 50ms、成功率 100%，容量余量充足（池 pending=0、GC 平稳）。
- 基线可复跑：`./scripts/bench.sh [requests] [concurrency]`；容量快照：`/actuator/prometheus`。
- 后续如并发上调（多策略/多任务），以 `POSTGRES_POOL_MAX` / `CLICKHOUSE_POOL_MAX` 按基线放大并复测。