# OBS-012 优化落地闭环（多标的 + 存档 + 应用） · 测试报告

> 分支：feature/obs-012-opt-apply · 日期：2026-10-07 · 状态：VERIFIED

## 1. 交付内容
| 产物 | 说明 |
|------|------|
| 多标的寻优 | `POST /api/backtest/optimize` 接受 `symbols[]`，逐标的寻优返回列表；**单标的失败不中断整批**（带 error 字段） |
| 存档表 | `strategy_optimization`（迁移 p2_011 + init-p2）：batchId/symbol/最优参数/指标/基准/bars/时间窗/runs_json |
| 应用接口 | `POST /api/backtest/optimize/apply?batchId=&strategyId=`：读存档 → **用最优参数复验回测** → 可选把参数**落档到策略 `params_json`**（归属校验） |
| 历史查询 | `GET /api/backtest/optimize/history?limit=` |
| 前端 | 优化面板支持多标的结果列表、批次显示、「应用最优（复验）」按钮 + 落档策略 ID 输入 + 复验指标展示 |

## 2. 实测（真实行情 E2E）
| 步骤 | 结果 |
|------|------|
| 多标的寻优（BTC 有数据 / ETH 无） | code 0，2 条：BTC best 10/20（ret -0.297%）；ETH 带 error 不中断 |
| ETH refresh 200 根后寻优 | ETH best 10/20（ret -0.0097%） |
| 应用最优（batchId + SIG-TRADE） | `applied=True`；复验 ret -0.2968%、交易 5、dd 0.328% |
| 参数落档确认 | `params_json={"fast":10,"slow":20,"source":"optimizer","batchId":"OPT-FC93125C","objective":"TOTAL_RETURN"}` |

## 3. 质量门禁
| 项 | 结果 |
|----|------|
| `StrategyOptimizerServiceTest` | 4/4（网格排序/回撤过滤/非法排除/应用复验） |
| Schema 测试 | init-p2 + p2_011 迁移 + 表列断言通过 |
| 全量 `mvn verify` | **273/273**（0 跳过，JaCoCo 通过） |
| 前端 `npm run build` | 通过 |

## 4. 说明
- 归档失败不影响优化返回（best-effort）；apply 无策略 ID 时仅复验不落档；所有权校验（非 ADMIN 仅本策略）。
- 多标的容错使批量寻优在部分标的缺数据时仍可用（带 error 定位）。

## 5. 结论
「寻优 → 存档 → 应用（复验 + 参数落档）」闭环打通；量化策略优化从研究到参数落档可用。