# TASK-P2-008 多租户横切扩展·组合

> 来源：P2 交付报告后续建议——将 tenant_id 横切扩展至 portfolio（用户资产第二落点）
> 依赖：P2-006 多租户（TenantContextHolder/TenantService）

## 节点
1. 迁移 p2_008：`portfolio.tenant_id`（存量 DEFAULT）+ 索引；`tenant.max_portfolios`。
2. `PortfolioService`：创建归属租户 + 配额校验；`requireOwned/requireReadable` 跨租户拒绝。
3. `TenantService`/控制器支持 maxPortfolios 配额。
4. 测试 + E2E。

## 验收
- 租户内建组合归属正确；超配额 409；跨租户访问 409；同租户正常；配额上调后可再建。
- 存量组合归 DEFAULT 不破坏既有行为。