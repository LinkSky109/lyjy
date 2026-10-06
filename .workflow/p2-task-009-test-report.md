# TASK-P2-009 多租户横切扩展·风险预算测试报告

> 分支：`feature/p2-009-tenant-risk-budget` · 日期：2026-10-06 · 状态：TEST_PASSED（观察期补强）

## 1. 自动化测试
| 测试层 | 结果 |
|--------|------|
| Java 单元与集成测试 | **252/252** 通过，0 跳过 |
| Testcontainers schema | 通过（含 p2_009 迁移） |
| JaCoCo 门禁 | 通过 |

新增：`RiskBudgetServiceTest` 4（新建归属租户、跨租户覆盖拒绝、列表按租户过滤、detail 跨租户拒绝 + ADMIN 豁免）；Schema 扩展。

## 2. E2E（真实 PG）
| 场景 | 结果 |
|------|------|
| 租户 TR-ALPHA 下保存预算 | `risk_budget.tenant_id=TR-ALPHA` |
| DEFAULT 风控跨租户覆盖 | 409「跨租户访问被拒绝」 |
| 列表过滤 | TR-ALPHA 可见 [ACC-ALPHA]；DEFAULT 不可见 [] |
| 跨租户 detail | 409；ADMIN 可读（code=0） |

## 3. 新增能力
- `risk_budget.tenant_id`（存量归 DEFAULT）+ 索引（迁移 p2_009）。
- `RiskBudgetService`：新建预算归属 `TenantContextHolder` 租户；覆盖/查看经 `requireTenantScope`（非 ADMIN 跨租户拒绝，ADMIN 豁免）；`list` 非 ADMIN 按租户过滤。
- 定时全量刷新（`refreshAll`）保持系统级，不因租户过滤而漏刷。

## 4. 说明
风险预算为三件租户数据（策略/组合/风险预算）的收尾落点；`refreshAll`/`scheduledRefresh` 为共享系统任务不受租户影响，占用计算与 BLOCK/WARN 联动不变。

## 5. 结论
预算归属、读写隔离、跨租户拦截与 ADMIN 豁免全部通过。多租户横切扩展（P2-008/009）完成。