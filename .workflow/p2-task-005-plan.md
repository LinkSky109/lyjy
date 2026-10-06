# TASK-P2-005 跨境交收状态机与多币种

> 需求：`requirements-supplement.md` 模块一 §1.4 / §1.1
> 依赖：P1-006（日终清算）

## 节点
1. `settlement_rule`（T+N 与币种）/ `settlement_task`（交收状态机）/ `currency_rate`（汇率）。
2. `SettlementTaskService`：生成（交易日→expected=+T+N）、到期交收 PENDING→SETTLED/FAILED、每日定时（锁）；`FxService`：直接/反向折算、upsert。
3. `/api/settlement/task/*`、`/api/currency/*`；写权限 FUND_MANAGER 交收 / ADMIN 汇率。
4. 测试 + E2E。

## 验收
- T+0/T+1/T+2 正确映射；任务到期自动交收、幂等。
- 汇率直接/反向折算正确；缺汇率报错。
- 非资金/管理员角色写被拒。