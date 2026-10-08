# OBS-019 策略风控可视化看板 · 测试报告

> 分支：feature/obs-019-risk-board · 日期：2026-10-08 · 状态：VERIFIED

## 1. 交付内容
| 层 | 产物 |
|----|------|
| 聚合服务 | `StrategyRiskOverviewService`：汇总运行策略数/熔断配置与触发数/组合风控/近期风控告警（strategy.circuit + portfolio.circuit） |
| API | `GET /api/strategy/risk/overview`（只读） |
| Mapper | `AlertEventMapper.selectByRuleIds`（按规则批量取最近告警） |
| 前端 | 新增「**风控看板**」页（`/risk-board`）：总览 Stat + 策略熔断表 + 组合风控表 + 近期风控告警表；10s 自动刷新；侧边栏入口 |

## 2. 实测（聚合 E2E）
构造：RB-A(RUNNING+熔断未触发)、RB-B(OFFLINE+已触发)、组合 PF-BOARD(已触发)、2 条 CRITICAL 告警。

| 输出 | 结果 |
|------|------|
| summary | running 1 / total 5 · circuitConfigured 2 · circuitTripped 1 · portfolios 1 · portfolioTripped 1 · recentAlerts 2 |
| 策略行 | RB-A RUNNING/启用/正常；RB-B OFFLINE/启用/**已触发** |
| 组合行 | PF-BOARD **已触发**，成员 `[RB-A, RB-B]` |
| 告警 | `strategy.circuit` CRITICAL(strategy:RB-B) · `portfolio.circuit` CRITICAL(portfolio:PF-BOARD) |

## 3. 质量门禁
| 项 | 结果 |
|----|------|
| `StrategyRiskOverviewServiceTest` | 1/1（聚合字段/计数/成员解析） |
| 全量 `mvn verify` | **288/288**（0 跳过，JaCoCo 通过） |
| 前端 `npm run build` | 通过 |

## 4. 说明
- 看板汇总单策略熔断（OBS-016/017）与组合风控（OBS-018）与告警，形成"看得见"的风控视图。
- 只读接口；策略页仍负责配置（熔断/值守/组合风控）。

## 5. 结论
策略/组合风控可视化闭环：状态、阈值、峰值、触发与告警在一屏可见。