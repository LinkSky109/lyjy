# TASK-P2-005 跨境交收状态机与多币种测试报告

> 分支：`feature/p2-005-settlement-fx` · 日期：2026-10-06 · 状态：TEST_PASSED

## 1. 自动化测试
| 测试层 | 结果 |
|--------|------|
| Java 单元与集成测试 | **232/232** 通过，0 跳过 |
| Testcontainers schema | 通过（含 p2_005 迁移） |
| JaCoCo 门禁 | 通过 |

新增：`SettlementTaskServiceTest` 5、`FxServiceTest` 5、Schema 扩展。

## 2. E2E（真实 PG）
| 场景 | 结果 |
|------|------|
| 交收规则 | CN 期货 T+0 CNY / A股 T+1 / 港股 T+2 HKD / 美股 T+2 USD |
| 生成任务（BASIC 交易日兼容） | 为 2026-10-05 成交生成交收任务，CNY / expected=2026-10-05 |
| 到期交收 asOf 2026-10-06 | `PENDING → SETTLED`（settled=2，system 留痕） |
| 汇率折算 | CNY→USD=14、CNY→HKD=108（直接/反向汇率） |
| 汇率 upsert | CNY/EUR 0.1305 → 即时可折算 |
| 权限 | FUND_MANAGER 更新汇率 → 403 |

## 3. 新增能力
- `settlement_rule`：按市场/交易所 T+N 与币种（种子：CN/HK/US）。
- `settlement_task`：交收状态机 `PENDING → SETTLING → SETTLED / FAILED`，`expected_date = 交易日 + T+N`；每日定时生成+到期交收（DistributedLock 互斥）；支持 BASIC/ISO 交易日格式。
- `currency_rate` + `FxService`：直接/反向汇率折算、缺汇率报错、upsert。
- 接口：`/api/settlement/task/*`（交收任务）、`/api/currency/*`（汇率与折算）。

## 4. 结论
交收周期适配（T+0/T+1/T+2）、状态流转、到期自动交收、多币种折算与权限全部通过。P2-005 满足准出。下一任务：P2-006 多租户隔离。