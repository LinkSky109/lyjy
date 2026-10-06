# TASK-P2-004 绩效归因测试报告

> 分支：`feature/p2-004-attribution` · 日期：2026-10-06 · 状态：TEST_PASSED

## 1. 自动化测试
| 测试层 | 结果 |
|--------|------|
| Java 单元与集成测试 | **221/221** 通过，0 跳过 |
| Testcontainers schema | 通过（含 p2_004 迁移） |
| JaCoCo 门禁 | 通过 |

新增：`AttributionServiceTest` 4（Brinson 恒等式、因子聚合、执行 IS、参数校验）、`RegulatorySchemaTest`、`SchemaMigrationIntegrationTest` 扩展。

## 2. E2E（真实 PG）
| 场景 | 结果 |
|------|------|
| Brinson 归因（2 组） | excess=0.008 = 分配0.004 + 选股0 + 交互0.004，**identityCheck=true**；落库 `ATT-99E16FF8` |
| 因子暴露归因 | size=0.4、momentum=0.41（Σ权重×暴露），落库 |
| 执行归因 IS | IS=200bps、滑点=99.01bps、notional=1020，落库 |
| 报告列表/详情 | 3 条报告，resultJson 可读 |
| 权限 | TRADER 触发计算 → HTTP 403 |

## 3. 新增能力
- `attribution_report`：归因结果按 BRINSON/FACTOR/EXECUTION + 作用域/周期落库，可审计追溯。
- Brinson：分配/选股/交互效应分解，`excess = rP − rB = Σ效应` 恒等式校验。
- 因子暴露：任意因子 × 权重 加权聚合。
- 执行归因：IS 与滑点（bps）+ 名目/手续费占比 + 收盘价机会成本占位。
- `/api/attribution/{brinson,factor,execution,report/list,report/{id}}`；写权限 COMPLIANCE/ADMIN。

## 4. 结论
三类归因计算、恒等式、落库与权限全部通过。P2-004 满足准出。下一任务：P2-005 跨境交收状态机与多币种。