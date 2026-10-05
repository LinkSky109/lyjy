# TASK-P1-008 VaR 风险价值测试报告

> 分支：`feature/p1-008-var`
> 日期：2026-10-05
> 状态：TEST_PASSED

## 1. 自动化测试

| 测试层 | 结果 |
|--------|------|
| Java 单元与集成测试 | 144/144 通过，0 跳过 |
| Testcontainers PostgreSQL schema | 通过（含 init-p1.sql 的 `var_calculation`） |
| Python 单元测试 | 23/23 通过 |
| Python 覆盖率 | 85.27% |
| JaCoCo 门禁 | 通过 |

新增用例：`VarCalculatorTest` 8 例、`VarServiceTest` 6 例、`VarSchemaTest` 1 例；
`IntradayRiskServiceTest` 新增 `VAR_LIMIT` 用例 1 例。

## 2. VaR E2E

环境：PG + ClickHouse（`lhjypt.bar_data` 播种 rb2410/au2410/cu2410 各 121 根日线）、
服务 8081、角色 RISK_OFFICER。

账户 `DEFAULT-001`（rb 2 + au 1 + cu 2，均 LONG）：

| 场景 | 结果 |
|------|------|
| HISTORICAL 95% 1d | calcId `VAR-973E27F6-8C1`，var=5982.12606881，samples=120，percentile=-5982.12606881 |
| VAR_COV 95% 1d | var=7290.03584559 |
| 置信度 99% | var=6097.93224141（> 95%） |
| 持有期 4 天 | var=11964.25213763（= 2 × 日 VaR，平方根缩放） |
| latest / history | 最近值与历史序列均正确返回 |

组合 `PF-3FD194B9`（rb 2 + au 1）：

| 场景 | 结果 |
|------|------|
| HISTORICAL 95% 1d | var=285.22245889 |
| 风险预算 `max_var_95_1d=1` | `budgetBreached=true`，`riskBudgetLimit=1` |

异常与权限：

| 场景 | 结果 |
|------|------|
| 持仓标的无行情（zz9999） | `422 INSUFFICIENT_DATA`，不产出虚假数值、不落库 |
| VIEWER / 无角色 POST compute | HTTP 403 |
| TRADER 读他人组合 VaR | 业务码 409「无权访问」 |
| VIEWER GET latest | 200（只读放行） |

事中风控联动：

- 规则 `VAR-LIMIT-1`（`{"maxVar95":1,"scopeType":"ACCOUNT","scopeId":"DEFAULT-001"}`，WARN）。
- 手动评估生成处置 `DSP-6784FC52`：`ACCOUNT:DEFAULT-001 VaR=5982.12606881 超过限额1`。
- `GET /api/risk/intraday/disposition/list` 可查。

## 3. 新增能力

- `VarCalculator`：历史模拟分位数（线性插值）+ 方差协方差（σ²=wᵀΣw，Acklam 逆正态），
  持有期平方根缩放；收益样本 < 60 抛 `INSUFFICIENT_DATA`。
- `VarService`：组合/账户/策略三种作用域持仓加载、历史日线拉取、计算落库、风险预算比对、
  最近值/历史查询；TRADER 组合数据范围隔离。
- `ClickHouseVarMarketDataSource`：基于 ClickHouse `bar_data` 日线，本地可运行、生产可替换。
- `VarController`：`/api/var/compute`、`/latest`、`/history` 三接口，
  `AuthInterceptor` 接入 VaR 写权限（RISK_OFFICER/TRADER）。
- `IntradayRiskService` 接入 `VAR_LIMIT` 规则类型：读取最近 VaR，超阈触发 WARN/BLOCK 处置。

## 4. 本轮修复

1. **BUG-016（P0 潜伏缺陷）**：`ClickHouseMarketDataRepository` 用 Lombok
   `@RequiredArgsConstructor` + 字段 `@Qualifier("clickHouseJdbcTemplate")`，
   但 Lombok 不把字段注解复制到构造参数，Spring 退化为按类型注入并命中 `@Primary` 的
   PostgreSQL `JdbcTemplate`。表现为所有 ClickHouse 行情读写实际打到 PG，
   抛 `bad SQL grammar / 25P02`，导致 `/api/market/bars` 与 VaR 行情读取全部为空。
   修复：改为显式构造注入并保留 `@Qualifier`（对齐 `MetricSampleMapper` 既有写法）。
2. `PositionMapper` 新增 `selectByStrategy`，支撑策略作用域 VaR。

## 5. 结论

- 计算正确性（分位数/协方差/置信度单调/平方根缩放）、数据不足、预算比对、
  权限隔离、`VAR_LIMIT` 联动全部通过。
- P1-008 满足准出标准，状态流转 `DEV_DONE -> TEST_PASSED`。
- 下一任务：TASK-P1-009 策略加密与沙箱。
