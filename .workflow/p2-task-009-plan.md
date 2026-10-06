# TASK-P2-009 多租户横切扩展·风险预算

> 来源：P2 交付报告后续建议——多租户横切至风险预算（策略/组合/风险预算三件租户数据收尾）
> 依赖：P2-006 多租户（TenantContextHolder/TenantService）

## 节点
1. 迁移 p2_009：`risk_budget.tenant_id`（存量 DEFAULT）+ 索引。
2. `RiskBudgetService`：新建归属租户；覆盖/查看 `requireTenantScope`（非 ADMIN 跨租户拒绝，ADMIN 豁免）；list 非 ADMIN 过滤。
3. 定时全量刷新保持系统级（不因租户过滤漏刷）。
4. 测试 + E2E。

## 验收
- 租户内建预算归属正确；跨租户覆盖/查看 409；list 按租户过滤；ADMIN 可跨租户只读。
- refreshAll/定时刷新与占用联动（BLOCK/WARN）不受影响。