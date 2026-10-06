# TASK-P2-001 监管报送与 AML 测试报告

> 分支：`feature/p2-001-regulatory-aml`
> 日期：2026-10-06
> 状态：TEST_PASSED

## 1. 自动化测试
| 测试层 | 结果 |
|--------|------|
| Java 单元与集成测试 | **200/200** 通过，0 跳过 |
| Testcontainers PostgreSQL schema | 通过（含 `p2_001_regulatory_aml.sql`） |
| JaCoCo 门禁 | 通过 |

新增用例：`RegulatoryReportServiceTest` 5、`AmlMonitoringServiceTest` 5、`RegulatorySchemaTest` 2；
同步扩展 `SchemaMigrationIntegrationTest`（新增 4 张表 + alert_id 断言）。

## 2. 监管报送 E2E（真实 PG，交易日 2026-10-06）
| 场景 | 结果 |
|------|------|
| 生成成交日报 DAILY_TRADE | `REG-DAILY_TRADE-2026-10-06-7333D8`，READY，2 条，金额 800800（700000+100800） |
| 幂等重跑 | 返回同一 batchId，不重复生成 |
| 生成大额报备 LARGE_TRADE | 仅纳入大额成交（700000 ≥ 阈值 50 万），1 条 / 700000 |
| 批次详情 | 明细行 2 条（含 tradeId/account/symbol/turnover 等） |
| 报送提交 | 无令牌 409；带令牌 → SUBMITTED，submitBy=comp-1 留痕 |
| 权限 | TRADER 触发报送 → HTTP 403 |

## 3. AML 监测 E2E
| 场景 | 结果 |
|------|------|
| 默认规则 | LARGE_SINGLE(≥50万) / LARGE_CUMULATIVE(≥100万/86400s) |
| 首次扫描 | 命中 `AML-34D824D9`（LARGE_SINGLE，700000）生成告警 OPEN |
| 重复扫描 | 去重生效，新增 0 条 |
| 告警列表/详情 | 返回 1 条 OPEN；evidence 含 tradeId/orderId/tradeTime |
| 上报 | 无令牌 409；带令牌 → REPORTED，handler=comp-1 |
| 关闭 | CLOSED |

## 4. 新增能力
- `regulatory_report_batch/item`：报送批次状态机 GENERATING→READY→SUBMITTED，`UNIQUE(report_type, report_date)` 幂等。
- `aml_rule`（默认 2 条种子）+ `aml_alert`：单笔大额 / 账户累计窗口监测，OPEN→REPORTED(令牌)→CLOSED 留痕。
- 服务：`RegulatoryReportService`（日报/大额报备生成、幂等、报送）、`AmlMonitoringService`（扫描去重、规则、上报/关闭）。
- 接口：`/api/regulatory/**` 11 个端点；`AuthInterceptor` 写权限 COMPLIANCE/ADMIN，报送与 AML 上报叠加一次性确认令牌。
- 脚本：`scripts/init-p2.sql`（P2 幂等增量，可挂 compose）+ `scripts/migration/p2_001_regulatory_aml.sql`（增量迁移）。

## 5. 本轮修复
`AmlAlertMapper.countOpenDedupe` 动态 SQL 未闭合 `<script>` 标签，`<if>` 原样下发导致扫描 500；补上 `</script>` 后由 E2E 复验通过。

## 6. 结论
报送批次幂等生成、金额汇总准确、报送留痕与权限；AML 大额/累计监测命中、去重、上报/关闭闭环全部通过。P2-001 满足准出，状态流转 `DEV_DONE -> TEST_PASSED`。下一任务：P2-002 策略水印与运行环境绑定。