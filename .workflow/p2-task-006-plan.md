# TASK-P2-006 多租户隔离

> 需求：PRD §14 + requirements-supplement（横切数据范围/配额）
> 依赖：全量

## 节点
1. `tenant` 表（状态 + 配额）；`strategy_info.tenant_id` 列（迁移 p2_006）。
2. `TenantContextHolder`（X-Tenant-Id → 上下文，默认 DEFAULT）+ AuthInterceptor 校验租户启用。
3. `TenantService` 注册/配额；策略上传归属租户并校验 max_strategies；非 ADMIN 跨租户访问拒绝。
4. `/api/tenant/*`（仅 ADMIN）；测试 + E2E。

## 验收
- 租户注册/配额生效；X-Tenant-Id 上下文正确。
- 超配额上传 409；未知/停用租户 403；跨租户访问 409。
- 其余模块沿用同一模式（本期以策略模块为落点）。