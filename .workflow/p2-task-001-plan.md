# TASK-P2-001 监管报送与 AML 执行计划

> 分支：`feature/p2-001-regulatory-aml`
> 需求：`requirements-supplement.md` 模块四（监管报送与合规）
> 目标：大额交易报备/成交日报的批次化生成与报送；AML 大额/累计可疑交易监测、规则可配、告警处置留痕。

## 节点
1. 表：`regulatory_report_batch` / `regulatory_report_item` / `aml_rule` / `aml_alert`（`scripts/init-p2.sql` + 增量迁移 `scripts/migration/p2_001_regulatory_aml.sql`）。
2. 实体与 Mapper（4 组）。
3. `RegulatoryReportService`：run(list/detail/items/submit) 批次状态机 GENERATING→READY→SUBMITTED，幂等。
4. `AmlMonitoringService`：scan() 按 `aml_rule` 扫描成交流（单笔/账户累计）生成告警，去重；告警列表/详情/上报（确认令牌）/关闭。
5. `RegulatoryController`：报送 + AML 两组接口；`AuthInterceptor` 接入 `/api/regulatory/**`（写仅 COMPLIANCE/ADMIN）。
6. 单元测试 + E2E（真实 PG 造数 → 生成批次 → AML 扫描 → 上报）。

## 业务规则
- 报送批次唯一键 `UNIQUE(report_type, report_date)`；幂等：已有 READY/SUBMITTED 批次直接返回，不重复生成。
- 上报为敏感操作需一次性确认令牌，落 submit_by/submit_time 留痕。
- AML 规则：`LARGE_SINGLE`（单笔成交额 ≥ threshold）、`LARGE_CUMULATIVE`（账户近 period 内累计额 ≥ threshold）。
- AML 去重：同 rule_code+account+strategy+amount 的 OPEN 告警不重复生成。
- 告警处置：`OPEN → REPORTED(需令牌) → CLOSED`，留 handler/handle_note。

## 接口
| 方法 | 路径 | 权限 |
|------|------|------|
| POST | `/api/regulatory/report/run?reportType=&reportDate=` | COMPLIANCE / ADMIN |
| GET | `/api/regulatory/report/list` | 全角色只读 |
| GET | `/api/regulatory/report/{batchId}`、`/items` | 全角色只读 |
| POST | `/api/regulatory/report/{batchId}/submit` | COMPLIANCE / ADMIN（令牌） |
| GET/POST | `/api/regulatory/aml/rules`、`/aml/rule` | 读全部 / 写 COMPLIANCE·ADMIN |
| POST | `/api/regulatory/aml/scan` | COMPLIANCE / ADMIN |
| GET | `/api/regulatory/aml/alerts`、`/aml/alert/{alertId}` | 全角色只读 |
| POST | `/api/regulatory/aml/alert/{alertId}/report`、`/close` | COMPLIANCE / ADMIN（令牌） |

## 验收
- LARGE_TRADE / DAILY_TRADE 批次生成正确、金额汇总一致、幂等可重入。
- 提交批次返回 SUBMITTED 且留痕；无令牌 409。
- AML 大额命中生成告警；同命中不重复；累计规则按窗口正确。
- 上报/关闭状态流转正确；权限：非 COMPLIANCE/ADMIN 写被拒。