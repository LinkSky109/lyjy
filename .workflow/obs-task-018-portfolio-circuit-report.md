# OBS-018 多策略组合风控 · 测试报告

> 分支：feature/obs-018-portfolio-circuit · 日期：2026-10-08 · 状态：VERIFIED

## 1. 交付内容
| 产物 | 说明 |
|------|------|
| `portfolio_circuit` 表（迁移 p2_013 + init-p2） | 组合标识/成员 JSON/阈值/启用/峰值/tripped/触发时间 |
| `PortfolioCircuitService` | @Scheduled（30s，锁）：聚合**运行中成员**持仓浮盈率（ΣunrealizedPnl / Σ|qty×avgCost|）→ 峰值回撤 ≥ 阈值 → **批量停止组内所有运行成员** + tripped + **告警落库/通知**（rule_id=portfolio.circuit） |
| API | `POST /api/strategy/portfolio-circuit?portfolioKey&members&maxDrawdownPct&enabled`（成员逐一归属校验）· `GET /api/strategy/portfolio-circuit/list` |
| 前端 | 策略页「组合风控」：组合标识 / 成员策略 / 阈值 / 启用关闭 |

## 2. 实测（真实 E2E）
| 步骤 | 结果 |
|------|------|
| 两成员 PC-A/PC-B 运行 | RUNNING |
| 成员持仓 | PC-A BTC 1@80000 pnl+500；PC-B ETH 1@2000 pnl−400 |
| 配置 PF-DEMO 阈值 1e-6 | enabled=true，members=["PC-A","PC-B"] |
| 定时扫描 | 第 6 轮 **A/B 均 STOPPED**；`tripped=t` + last_trip_time |
| 告警 | `EVT-4f36fe9b · portfolio.circuit · CRITICAL`（当前浮盈率 0.00121951 = (500−400)/(80000+2000) 与聚合一致） |

（过程中修正：成员策略源码漏实现 `on_init`（抽象方法）导致沙箱实例化失败 → 补齐后正常。）

## 3. 质量门禁
| 项 | 结果 |
|----|------|
| `PortfolioCircuitServiceTest` | 3/3（超限批量停止+告警 / 未超仅更新峰值 / 无运行成员跳过） |
| Schema 测试 | p2_013 迁移 + 表列断言通过 |
| 全量 `mvn verify` | **287/287**（0 跳过，JaCoCo 通过） |
| 前端 `npm run build` | 通过 |

## 4. 说明
- 组合风控与单策略熔断（OBS-016）可并存，各自独立触发与停止。
- 无运行成员或无持仓成本时不评估；重新配置即复位 tripped/峰值。

## 5. 结论
组合级风控闭环：多策略成员聚合回撤 → 批量处置 + 告警；与 OBS-014 组合分配衔接（分配后整体设风控）。