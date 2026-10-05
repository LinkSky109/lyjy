# TASK-P1-008 VaR 风险价值执行计划

> 分支：`feature/p1-008-var`
> 需求：`docs/p1-prd.md` §5.7、§4 表 `var_calculation`
> 目标：组合/账户/策略在给定置信度下的最大可能损失计算（历史模拟默认 + 方差协方差），记录与查询，联动风险预算与事中风控 `VAR_LIMIT`。

## 节点

1. `VarCalculation` 实体 + `VarCalculationMapper`（`var_calculation` 表，P1 已建，无新增 DDL）。
2. `VarMarketDataSource` 行情抽象 + ClickHouse `bar_data` 日线实现（本地可运行，生产可替换）。
3. `VarCalculator` 纯计算引擎：历史模拟分位数 + 方差协方差，含样本不足校验。
4. `VarService` 编排：加载持仓、拉取历史行情、计算、落库、风险预算比对、最近值/历史查询。
5. `VarController` 3 接口；`AuthInterceptor` 权限接入。
6. `IntradayRiskService` 接入 `VAR_LIMIT` 规则类型，联动 `VAR_LIMIT` 处置。
7. 单元测试 + 真实 E2E（ClickHouse 播种日线 → 造持仓 → 计算 → 查询）。

## 业务规则

- 方法：`HISTORICAL`（默认，1d 95%）、`VAR_COV`。
- 输入：持仓（PORTFOLIO=`portfolio_position.cur_qty`，ACCOUNT/STRATEGY=`position`）+ 历史日线收盘价。
- 日损益序列：`pnl_t = Σ sign_i × qty_i × close_i,last × ret_i,t`；空头方向取负。
- 历史模拟：取 `1-confidence` 分位数，`VaR = -quantile × sqrt(horizon)`。
- 方差协方差：`σ² = wᵀΣw`，`VaR = z_confidence × σ × sqrt(horizon)`，z 用 Acklam 逆正态。
- 样本不足（收益样本 < 60）→ `INSUFFICIENT_DATA`，不产出虚假数值。
- 风险预算：`portfolio.config_json.risk_budget.max_var_95_1d` 比对，超限标记 `budgetBreached`。
- 事中风控：`VAR_LIMIT` 规则读取最近 VaR，超 `maxVar95` 触发 `WARN/BLOCK` 处置。
- 幂等：仅记录，无写副作用；`calc_id` 唯一。

## 接口

| 方法 | 路径 | 权限 |
|------|------|------|
| POST | `/api/var/compute` | RISK_OFFICER / TRADER（自属） |
| GET | `/api/var/latest?scopeType=&scopeId=` | 全角色只读 |
| GET | `/api/var/history?scopeType=&scopeId=&from=&to=` | 全角色只读 |

## 验收

- 历史模拟分位数正确（构造已知序列验证 95% 分位）。
- 数据不足返回 `INSUFFICIENT_DATA`。
- VaR 随置信度升高而增大；horizon 按 sqrt 缩放。
- 计算落 `var_calculation`，`latest`/`history` 可查。
- 组合预算超限标记 `budgetBreached=true`；`VAR_LIMIT` 规则触发事中处置。
- PORTFOLIO 作用域 TRADER 越权访问被拒绝。
