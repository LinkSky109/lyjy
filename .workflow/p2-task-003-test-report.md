# TASK-P2-003 风险预算分配与占用监控测试报告

> 分支：`feature/p2-003-risk-budget` · 日期：2026-10-06 · 状态：TEST_PASSED

## 1. 自动化测试
| 测试层 | 结果 |
|--------|------|
| Java 单元与集成测试 | **216/216** 通过，0 跳过 |
| Testcontainers schema | 通过（含 p2_003 迁移） |
| JaCoCo 门禁 | 通过 |

新增：`RiskBudgetServiceTest` 6、`RegulatorySchemaTest` 扩展 2、`SchemaMigrationIntegrationTest` 补 p2_003 断言。

## 2. E2E（真实 PG + VaR 数据）
账户 DEFAULT-001 最近 VaR=5982.126。

| 场景 | 结果 |
|------|------|
| 保存账户预算 limit=100 | `BUD-6D40950C` 创建，OK |
| 刷新占用 | VaR 5982.126 → usage=59.821 → **BLOCK** |
| 开仓限制联动 | `GET /api/risk/intraday/block/list` → `["ACCOUNT:DEFAULT-001"]` |
| 策略预算（无 VaR 记录） | usage=0 → OK |
| 权限 | TRADER 保存预算 → HTTP 403 |
| 回落（limit=100000） | usage=0.0598 → **OK**，block 清单自动解除 |

## 3. 新增能力
- `risk_budget`（ACCOUNT/PORTFOLIO/STRATEGY × VAR 口径，limit/current/usage_rate/status，`UNIQUE(scope,type)`）。
- `RiskBudgetService`：保存/列表/单条刷新/全量定时刷新（30s，分布式锁保护）；占用 ≥100% → BLOCK（联动 OpenBlockRegistry 限制开仓），≥80% → WARN，回落自动解禁。
- `RiskBudgetController`：`/api/risk/budget/**`（写仅 RISK_OFFICER/ADMIN）。

## 4. 结论
预算配置、占用刷新、三档状态（WARN/BLOCK）、开仓限制联动与自动解禁、权限拦截全部通过。P2-003 满足准出。下一任务：P2-004 绩效归因（Brinson + 因子暴露）。