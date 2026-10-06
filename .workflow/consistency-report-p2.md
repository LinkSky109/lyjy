# P2 一致性校验报告

> 任务ID: TASK-P2-FULL（P2 三期 7 任务批次）
> 巡检日期: 2026-10-06
> 最终状态: CONSISTENCY_PASS

---

## 1. 四维结果

| 维度 | 结果 | 说明 |
|------|------|------|
| 代码一致性 | 通过 | 构建、244 个 Java 测试、23 个 Python 测试、覆盖率门禁和敏感信息检查通过 |
| 环境一致性 | 通过 | Lima Docker、PG/CH/Kafka/Redis 健康，Spring Boot Jar 可启动，前端 5173 可达 |
| 数据一致性 | 通过 | Testcontainers schema 通过（init-p2 + 7 个增量迁移），真实 PG 12 张 P2 表全部存在，E2E 数据闭环正确 |
| 文档一致性 | 通过 | requirements-supplement、p2-plan、7 组 task plan/test-report、context.yaml 一致 |

---

## 2. 代码一致性

| 检查项 | 结果 |
|--------|------|
| Java 构建 | `mvn verify`（Testcontainers + JaCoCo）通过 |
| Java 测试 | 244/244 通过，0 跳过 |
| Python 测试 | 23/23 通过（85.27%） |
| JaCoCo 门禁 | 通过 |
| 分支独立性 | P2-001~007 各 feature 分支 + 独立提交（f7eb1c7→c88e9fc） |
| 工作区 | 干净，无未提交改动 |

## 3. 环境一致性

| 组件 | 实测 | 结果 |
|------|------|------|
| JDK | 17.0.20.1 | 通过 |
| PostgreSQL | 15-alpine（重启后连通） | 通过 |
| ClickHouse / Kafka / Redis | 5 容器 healthy | 通过 |
| 核心服务 | 8081，健康检查 UP | 通过 |
| 可执行 Jar | Spring Boot repackage 成功 | 通过 |
| 管理前端 | 5173（Vite，proxy→8081）http 200 | 通过 |

## 4. 数据一致性

| 检查项 | 结果 |
|--------|------|
| init-p2.sql（幂等） | Testcontainers 通过 |
| p2_001~007 迁移（幂等/增量） | Testcontainers 全执行通过 |
| 真实 PG 表核对 | 12 张 P2 表全部存在（aml_rule/alert、watermark、risk_budget、attribution、settlement_rule/task、currency_rate、tenant、capacity_snapshot 等） |
| P2-001 报送闭环 | 大额成交 → 批次 READY(1 项/999900) → 确认令牌提交 SUBMITTED |
| P2-002 水印 | 上传 → watermark=True |
| P2-003 预算 | VAR 预算保存/刷新/list 正常 |
| P2-004 归因 | Brinson 报告生成 + list 正常 |
| P2-005 结算/FX | 汇率 upsert、USD→CNY 1000→7200；无网关时对账 409 优雅返回 |
| P2-006 租户 | DEFAULT ACTIVE；配额/跨租户/未知租户拦截已验（任务期） |
| P2-007 容量 | 手动+定时采样落库、latest/history/autoscale 正常 |
| E2E 数据清理 | 回归数据全部清理（trade_record 0、批次 0、租户保留 DEFAULT、aml_rule 保留 2 条种子） |

## 5. 安全一致性

| 检查项 | 结果 |
|--------|------|
| 敏感操作二次确认 | 报送提交需 X-Confirm-Token（30s 一次性） |
| 越权拦截 | /api/tenant、/api/capacity、/api/currency 写操作限 ADMIN；跨租户访问 409 |
| 密钥 | 无新增硬编码，KMS 适配层沿用 |

## 6. 文档一致性

| 文档 | 状态 |
|------|------|
| requirements-supplement | 模块一~五与实现覆盖对齐 |
| p2-plan.md | 7/7 全部完成 |
| p2-task-001~007 plan/test-report | 全部生成 |
| context.yaml | P2 7/7，收尾完成 |
| 交付报告 | 已生成（delivery-report-p2.md） |

## 7. 结论

- 代码、环境、数据、安全、文档五个维度全部通过；无阻断项与未闭环问题。
- 状态流转 `CONSISTENCY_PASS`；P2 三期（合规/多租户/自动伸缩）可交付。