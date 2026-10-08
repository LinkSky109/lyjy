# OBS-016 策略回撤熔断 · 测试报告

> 分支：feature/obs-016-circuit-breaker · 日期：2026-10-08 · 状态：VERIFIED

## 1. 交付内容
| 产物 | 说明 |
|------|------|
| `strategy_circuit` 表（迁移 p2_012 + init-p2） | 阈值/启用/峰值浮盈率/tripped/触发时间 |
| `StrategyCircuitBreakerService` | @Scheduled（30s，DistributedLock）跟踪运行中策略：**持仓浮盈率 = ΣunrealizedPnl / Σ|qty×avgCost|**，维护历史峰值，回撤 ≥ 阈值 → **自动停止策略**（system 上下文）并标记 tripped |
| API | `POST /api/strategy/{id}/circuit?maxDrawdownPct&enabled`（归属校验）· `GET /api/strategy/circuit/list` |
| 前端 | 策略页运行 tab「回撤熔断」：阈值输入 + 启用/关闭 + 状态提示 |

## 2. 实测（真实 E2E）
| 步骤 | 结果 |
|------|------|
| 运行策略建仓 | 1 手 BTC_USDT @85557.8（RUNNING） |
| 配置熔断 | 阈值 1e-6、enabled=true |
| 构造浮亏 | 持仓浮亏 −10000（浮盈率 ≈ −0.117）、峰值 0.1 |
| 定时扫描 | **策略自动 STOPPED**；记录 `tripped=t`、`last_trip_time` |

## 3. 质量门禁
| 项 | 结果 |
|----|------|
| `StrategyCircuitBreakerServiceTest` | 3/3（超阈值触发停止/未超仅更新峰值/未运行或无持仓跳过） |
| Schema 测试 | p2_012 迁移 + 表列断言通过 |
| 全量 `mvn verify` | **283/283**（0 跳过，JaCoCo 通过） |
| 前端 `npm run build` | 通过 |

## 4. 说明
- 熔断指标为**策略层持仓浮盈率峰值回撤**（免账户级耦合）；无持仓成本时不评估。
- 重新配置阈值即复位 tripped/峰值。
- 触发动作调用 `StrategySecurityService.stop`（含审计），后续可接入告警通知通道。

## 5. 结论
策略回撤熔断闭环：阈值配置 → 定时监控 → 超限自动停止 + 留痕，与优化参数落档后的实盘风控衔接。