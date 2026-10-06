# TASK-P2-006 多租户隔离测试报告

> 分支：`feature/p2-006-multi-tenant` · 日期：2026-10-06 · 状态：TEST_PASSED

## 1. 自动化测试
| 测试层 | 结果 |
|--------|------|
| Java 单元与集成测试 | **239/239** 通过，0 跳过 |
| Testcontainers schema | 通过（含 p2_006 迁移） |
| JaCoCo 门禁 | 通过 |

新增：`TenantServiceTest` 4；`StrategySecurityServiceTest` 更新至 11（含配额/跨租户）；`AuthInterceptorTest` 适配；Schema 扩展。

## 2. E2E（真实 PG）
| 场景 | 结果 |
|------|------|
| 创建租户 T-ALPHA（maxStrategies=1） | ACTIVE，配额 1 |
| 租户内首支策略上传 | code 0，`strategy_info.tenant_id=T-ALPHA` |
| 配额拦截（第二支） | 409「租户策略配额已用完」 |
| 未知租户请求 | HTTP 403（拦截器校验租户启用） |
| 跨租户访问（DEFAULT 用户 → T-ALPHA 策略） | 409「跨租户访问被拒绝」 |
| 同租户访问 | 水印查看正常 |

## 3. 新增能力
- `tenant` 注册表（status + max_strategies/accounts/daily_orders 配额），`X-Tenant-Id` 注入 `TenantContextHolder`（默认 DEFAULT，非 ADMIN 校验租户启用）。
- 策略数据按租户隔离：`strategy_info.tenant_id`；上传归属租户并校验配额；非 ADMIN 跨租户访问被拒绝。
- `/api/tenant/*`（租户注册/配额，仅 ADMIN）。

## 4. 说明
以策略模块作为多租户落点（其余模块沿用同一 `TenantContextHolder` + 归属/校验模式）；租户配额覆盖策略数，账户/日单配额字段已预留。

## 5. 结论
租户注册、配额、`X-Tenant-Id` 上下文、跨租户拦截全部通过。P2-006 满足准出。下一任务：P2-007 容量管理与自动伸缩。