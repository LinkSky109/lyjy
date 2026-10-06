# TASK-P2-003 风险预算分配与占用监控

> 需求：`requirements-supplement.md` 模块二 §2.3
> 依赖：P1-008 VaR

## 节点
1. `risk_budget` 表（init-p2.sql + 迁移 p2_003）。
2. `RiskBudgetService`：保存/列表/刷新；VaR 口径占用率；OK/WARN(≥80%)/BLOCK(≥100%)；BLOCK 联动 OpenBlockRegistry 限制开仓、回落解禁；30s 定时刷新（DistributedLock）。
3. `RiskBudgetController` `/api/risk/budget/**`；写权限 RISK_OFFICER/ADMIN。
4. 测试 + E2E。

## 验收
- 预算配置/占用刷新正确；三档状态；BLOCK 注册限制开仓，回落自动解除。
- 定时刷新多实例互斥；无 VaR 数据占用 0。
- 非风控角色写被拒。