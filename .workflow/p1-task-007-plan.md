# TASK-P1-007 组合管理执行计划

> 分支：`feature/p1-007-portfolio`
> 需求：`docs/p1-prd.md` §5.6
> 目标：组合定义、目标持仓、约束校验、再平衡预览、确认下单和结果回写。

## 节点

1. 组合、组合持仓、再平衡任务实体与 Mapper。
2. 组合创建、查询和目标持仓设置。
3. RebalanceEngine 计算买卖指令、预计成本、约束违规。
4. 确认执行：逐指令调用统一交易入口，资金不足跳过并继续。
5. 7 个 API、权限控制和确认口令。
6. 单元测试与真实 E2E。

## 业务规则

- 组合 ID 唯一，默认执行模式 MANUAL。
- 同一组合同时只允许一个 ACTIVE 再平衡任务。
- 触发类型 MANUAL/THRESHOLD/SCHEDULED/EVENT。
- 任务状态 PENDING/PREVIEW_READY/RUNNING/COMPLETED/PARTIALLY_COMPLETED/FAILED。
- `config_json` 包含账户、价格、单标的权重上限和杠杆上限。
- 目标数量大于当前数量生成 BUY OPEN，小于则生成 SELL CLOSE。
- 单标的权重超限、杠杆超限、缺少价格或显式 weightLimit 超限时指令标记 REJECTED。
- 确认执行使用 `jobId + instructionIndex` 作为幂等键。
- 单条指令资金不足或风控拒绝时标记 FAILED，其他指令继续。
- 成功指令回写 `portfolio_position.cur_qty`。

## 接口

| 方法 | 路径 | 权限 |
|------|------|------|
| POST | `/api/portfolio` | TRADER |
| GET | `/api/portfolio/list` | TRADER/RISK/ADMIN |
| POST | `/api/portfolio/{portfolioId}/positions` | TRADER |
| GET | `/api/portfolio/{portfolioId}` | TRADER/RISK/ADMIN |
| POST | `/api/portfolio/{portfolioId}/rebalance` | TRADER |
| POST | `/api/portfolio/rebalance/{jobId}/preview` | TRADER |
| POST | `/api/portfolio/rebalance/{jobId}/confirm` | TRADER + 确认口令 |

## 验收

- 预览包含买卖方向、开平、数量、价格、预计成本和状态。
- 超限指令不进入确认执行。
- 确认后成功指令生成 OMS 订单，部分失败不影响其他指令。
- 重复确认返回同一任务结果。
- 不同组合可并行，同组合互斥。
