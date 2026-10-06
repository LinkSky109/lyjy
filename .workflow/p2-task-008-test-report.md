# TASK-P2-008 多租户横切扩展·组合测试报告

> 分支：`feature/p2-008-tenant-scope` · 日期：2026-10-06 · 状态：TEST_PASSED（观察期补强）

## 1. 自动化测试
| 测试层 | 结果 |
|--------|------|
| Java 单元与集成测试 | **247/247** 通过，0 跳过 |
| Testcontainers schema | 通过（含 p2_008 迁移） |
| JaCoCo 门禁 | 通过 |

新增：`PortfolioServiceTest` 2（组合归属+配额、跨租户拒绝）；`TenantServiceTest` 扩展 maxPortfolios；Schema 扩展（迁移增量 + 列断言）。

## 2. E2E（真实 PG）
| 场景 | 结果 |
|------|------|
| 创建租户 TP-ALPHA（maxPortfolios=1） | ACTIVE，组合配额 1 |
| 租户内首组合 | `portfolio.tenant_id=TP-ALPHA` |
| 组合配额（第二个） | 409「租户组合配额已用完」 |
| 跨租户访问（DEFAULT 用户 → TP-ALPHA 组合） | 409「跨租户访问被拒绝」 |
| 同租户访问 | 正常（detail code=0） |
| 配额上调 maxPortfolios=3 | 同租户可再建（code=0） |

## 3. 新增能力
- `portfolio.tenant_id`（存量归 DEFAULT）+ 索引；`tenant.max_portfolios` 组合配额。
- `PortfolioService`：创建归属租户并校验配额；`requireOwned/requireReadable` 非 ADMIN 跨租户拒绝（`requireTenantScope` 统一校验点）。
- `/api/tenant/{id}/quota` 支持 `maxPortfolios`。

## 4. 说明
本次将多租户隔离从策略横切到组合（用户资产的第二个落点）；资金/结算等平台共享层维持共享，`TenantContextHolder` 模式可继续推广。

## 5. 结论
组合数据隔离、配额与跨租户拦截全部通过。P2 观察期补强完成。