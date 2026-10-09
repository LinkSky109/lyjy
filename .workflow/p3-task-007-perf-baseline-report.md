# P3-007 性能与容量基线 · 测试报告

> 分支：feature/p3-007-perf-baseline · 日期：2026-10-09 · 状态：VERIFIED

## 1. 交付内容
| 产物 | 说明 |
|------|------|
| `scripts/bench.sh` | 关键路径压测（health / market.bars / trade.positions / risk.overview / backtest.run），输出 p50/p95/p99/max + 成功率 + 吞吐（Markdown 表） |
| 连接池显式化 | `spring.datasource.primary/clickhouse` 直接绑定 Hikari 参数：pool-name、max/min、conn-timeout、idle/max-lifetime、validation |
| `docs/perf-baseline.md` | 两轮基线对比、容量快照（池/Heap/GC）、调优项与复跑方式 |

## 2. 基线结果（`bench.sh 60 6`，调优后）
| 端点 | 成功率 | p50 | p95 | rps |
|------|--------|-----|-----|-----|
| health | 100% | 9.6ms | 43.7ms | 395 |
| market.bars | 100% | 16.7ms | 47.6ms | 303 |
| trade.positions | 100% | 5.9ms | 10.3ms | 908 |
| strategy.risk.overview | 100% | 8.0ms | 14.4ms | 689 |
| backtest.run(1000 tick) | 100% | 19.9ms | 55.4ms | 78 |

## 3. 容量与调优验证
- 池显式生效：`hikaricp_connections_max{pool="lhjypt-pg"}=20`、`{pool="lhjypt-ch"}=10`（此前匿名 HikariPool-1/2）。
- 压测后：active/pending/timeout_total 均 0；Heap Old ≈46MB；GC 少量（G1）。
- 对比调优前：各端点持平或更优（positions p50 6.1→5.9，risk.overview p50 7.8→8.0，回测 21.6→19.9）。

## 4. 质量门禁
| 项 | 结果 |
|----|------|
| `bash -n scripts/bench.sh` | 通过 |
| YAML 校验 | 通过 |
| 全量 `mvn verify` | **294/294**（0 跳过，JaCoCo 通过；本轮无 Java 逻辑改动，仅配置） |
| 启动与池校验 | 通过（pool-name/max 生效） |

## 5. 结论
性能与容量基线建立且调优落地（P3-007 完成，P3 全部 7 项完成）。