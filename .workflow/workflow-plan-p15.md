# P1.5 持续工作流执行计划

> 工作流ID: dev-test-consistency-p15
> 任务ID: TASK-20261005-002
> 任务名称: P1.5 交易主链路闭环与工程化加固
> 模式: 公开版半自动化，AI 管控流程并生成指令，人工完成 Git 初始化、审批和外部工具操作
> 当前状态: GLOBAL_IN_DEV
> 当前开发子状态: PENDING_DEV

---

## 1. 工作流配置

```yaml
workflow:
  id: dev-test-consistency-p15
  version: 1.0.0
  scope: "交易主链路闭环、持久化、权限、环境和质量门槛"
  task_id: "TASK-20261005-002"
  requirement_doc: "docs/p1.5-trade-closure-requirements.md"
  tasks_doc: ".workflow/tasks-p15.md"

tools:
  git: "required-before-development"
  ci: "github-actions"
  middleware: "docker-compose"
  testcontainers: "required"
  notify_channel: "codex-thread"

sla:
  dev_cycle_hours: 72
  code_review_hours: 24
  test_cycle_hours: 48
  bug_fix_hours: 12
  escalation_hours: [2, 8, 24, 48]

quality_gates:
  java_coverage_percent: 80
  python_coverage_percent: 80
  new_code_coverage_percent: 85
  lint_block_level: error
  integration_test_required: true
  sensitive_scan_required: true
  consistency_blocking_dimensions: [code, data, doc]
```

---

## 2. 全局状态与子状态映射

| 全局状态 | 开发子状态 | 测试子状态 | 一致性状态 | 进入条件 |
|----------|------------|------------|------------|----------|
| GLOBAL_PENDING | 无 | 无 | 无 | 需求录入但未启动 |
| GLOBAL_IN_DEV | PENDING_DEV/IN_DEV/CODE_REVIEW | 无 | 无 | 需求确认或任务开发中 |
| GLOBAL_DEV_BLOCKED | DEV_BLOCKED | 无 | 无 | 开发阻塞且无法自动处理 |
| GLOBAL_IN_TEST | DEV_DONE | PENDING_TEST/IN_TEST/BUG_FIXING | 无 | 开发准出全部满足 |
| GLOBAL_TEST_BLOCKED | DEV_DONE | TEST_BLOCKED | 无 | 环境或严重缺陷阻塞 |
| GLOBAL_IN_CONSISTENCY | DEV_DONE | TEST_PASSED | 校验中 | 测试准出全部满足 |
| GLOBAL_DONE | DEV_DONE | TEST_PASSED | CONSISTENCY_PASS | 全流程完成 |

当前为 `GLOBAL_IN_DEV / PENDING_DEV`。只有完成 Git 基线和任务分配后，才允许流转到 `IN_DEV`。

---

## 3. 节点定义

### NODE-P15-000 开发准入

```yaml
node:
  id: NODE-P15-000
  name: 开发准入与基线治理
  pre_state: PENDING_DEV
  trigger_event: "plan.approved"
  executor: dev_agent + human
  sla: 8h
  actions:
    - 确认需求文档和开放问题
    - 初始化 Git 基线
    - 创建 feature/p15-trade-closure 分支
    - 固化当前构建和测试基线
  success_criteria:
    - Git 仓库和基线提交存在
    - feature 分支存在
    - mvn clean verify 通过
    - Python 测试基线已记录
    - 任务 P15-000 至 P15-012 已分配
  fail_handler:
    action: pause
    target_state: PENDING_DEV
    exception_code: E010
  next_state:
    success: IN_DEV
    fail: DEV_BLOCKED
```

### NODE-P15-001 数据模型迁移

```yaml
node:
  id: NODE-P15-001
  name: 交易数据模型与迁移
  pre_state: IN_DEV
  trigger_event: "P15-001.started"
  executor: dev_agent + database-reviewer
  sla: 24h
  actions:
    - 扩展 orders 和幂等表
    - 新增 trade_record/order_fund_reservation/gateway_report_log
    - 新增 Position 实体和 Mapper
    - 编写增量迁移脚本
    - 增加 schema 集成测试
  success_criteria:
    - 迁移脚本可在空库和 P1 基线库重复执行
    - 表结构与数据字典一致
    - 旧数据不丢失
  fail_handler:
    action: rollback
    target_state: IN_DEV
    exception_code: E008
  next_state:
    success: IN_DEV
    fail: DEV_BLOCKED
```

### NODE-P15-002 统一订单编排

```yaml
node:
  id: NODE-P15-002
  name: 统一订单编排服务
  pre_state: IN_DEV
  trigger_event: "P15-001.done"
  executor: dev_agent + reviewer
  sla: 48h
  actions:
    - 实现 TradeApplicationService
    - 新增 POST /api/trade/orders
    - 旧入口切换兼容层
    - 接入幂等、状态机、审计和补偿接口
    - 编写并发和失败路径测试
  success_criteria:
    - 所有订单必须经过统一入口
    - 10 并发同幂等键只建一单
    - 风控拒绝无冻结无报单
    - 网关超时可进入 SUBMIT_UNKNOWN
  fail_handler:
    action: rollback
    target_state: IN_DEV
    exception_code: E005
  next_state:
    success: CODE_REVIEW
    fail: DEV_BLOCKED
```

### NODE-P15-003 风控主链路接入

```yaml
node:
  id: NODE-P15-003
  name: 真实风控上下文与决策闭环
  pre_state: IN_DEV
  trigger_event: "P15-002.core-ready"
  executor: dev_agent + security-reviewer
  sla: 24h
  actions:
    - 实现 TradingRiskContextFactory
    - 接入 PG 账户、持仓和频率
    - 接入 OpenBlockRegistry
    - 记录风控拦截日志和指标
    - 压测单次风控耗时
  success_criteria:
    - 风控不再使用固定模拟值
    - 规则异常默认拒绝
    - 开仓限制生效，平仓放行
    - 平均风控耗时低于 1ms
  fail_handler:
    action: rollback
    target_state: IN_DEV
    exception_code: E004
  next_state:
    success: IN_DEV
    fail: DEV_BLOCKED
```

### NODE-P15-004 资金闭环

```yaml
node:
  id: NODE-P15-004
  name: 资金冻结与结算闭环
  pre_state: IN_DEV
  trigger_event: "P15-003.core-ready"
  executor: dev_agent + reviewer
  sla: 36h
  actions:
    - 实现 FundReservationService
    - 冻结和释放以 orderId 幂等
    - 成交后结算保证金、手续费和盈亏
    - 所有变化写入 fund_flow
    - 增加资金守恒测试
  success_criteria:
    - 重复冻结和释放不重复记账
    - 可用、冻结、保证金关系可解释
    - 部分成交撤单正确释放
  fail_handler:
    action: rollback
    target_state: IN_DEV
    exception_code: E005
  next_state:
    success: IN_DEV
    fail: DEV_BLOCKED
```

### NODE-P15-005 回报流水线

```yaml
node:
  id: NODE-P15-005
  name: 网关回报流水线与幂等处理
  pre_state: IN_DEV
  trigger_event: "P15-002/003/004.done"
  executor: dev_agent + reviewer
  sla: 48h
  actions:
    - 注册订单和成交监听器
    - 实现回报落库和去重
    - 实现 OMS、持仓、资金分发
    - 处理乱序、部分成交和重复回报
    - 增加有界队列和重试
  success_criteria:
    - 同一 reportKey 只处理一次
    - 重复回报 100 次无重复记账
    - 乱序回报可以收敛
    - 队列满可观测且不静默丢弃
  fail_handler:
    action: retry
    target_state: IN_DEV
    exception_code: E005
  next_state:
    success: IN_DEV
    fail: DEV_BLOCKED
```

### NODE-P15-006 持仓持久化

```yaml
node:
  id: NODE-P15-006
  name: 成交与持仓持久化
  pre_state: IN_DEV
  trigger_event: "P15-005.done"
  executor: dev_agent + reviewer
  sla: 24h
  actions:
    - 持久化成交记录
    - 更新 OMS 累计成交
    - 实现加权平均持仓成本
    - 阻止负持仓和超卖
    - 增加重启恢复测试
  success_criteria:
    - 重启后持仓和成交可查询
    - 重复成交不改变持仓
    - 正常流程 PG 与网关持仓一致
  fail_handler:
    action: rollback
    target_state: IN_DEV
    exception_code: E005
  next_state:
    success: IN_DEV
    fail: DEV_BLOCKED
```

### NODE-P15-007 对账与恢复

```yaml
node:
  id: NODE-P15-007
  name: 对账、恢复与补偿
  pre_state: IN_DEV
  trigger_event: "P15-006.done"
  executor: dev_agent + reviewer
  sla: 24h
  actions:
    - 启动恢复活跃订单和未知订单
    - 扩展资金和持仓对账
    - 生成差异记录
    - 提供人工补偿接口
  success_criteria:
    - 正常流程 MATCHED
    - 人为差异可识别
    - SUBMIT_UNKNOWN 可收敛
  fail_handler:
    action: pause
    target_state: IN_DEV
    exception_code: E008
  next_state:
    success: IN_DEV
    fail: DEV_BLOCKED
```

### NODE-P15-008 运行模式治理

```yaml
node:
  id: NODE-P15-008
  name: 运行模式与网关适配边界
  pre_state: IN_DEV
  trigger_event: "P15-000.done"
  executor: dev_agent + reviewer
  sla: 8h
  actions:
    - 定义 MOCK/LIVE 模式
    - 启动前校验适配器
    - 删除 LIVE 到 Mock 的静默回退
    - 健康检查暴露运行模式
  success_criteria:
    - LIVE 无适配器时明确失败
    - MOCK 模式测试通过
  fail_handler:
    action: rollback
    target_state: IN_DEV
    exception_code: E004
  next_state:
    success: IN_DEV
    fail: DEV_BLOCKED
```

### NODE-P15-009 权限与可观测性

```yaml
node:
  id: NODE-P15-009
  name: 权限、审计与可观测性
  pre_state: IN_DEV
  trigger_event: "P15-002.done"
  executor: dev_agent + security-reviewer
  sla: 36h
  actions:
    - 新增认证拦截器
    - 实现角色和数据范围
    - 关键操作写审计日志
    - 增加 traceId 和交易指标
  success_criteria:
    - 未授权操作无副作用
    - 敏感操作完整审计
    - 可按 orderId/traceId 检索链路
  fail_handler:
    action: rollback
    target_state: IN_DEV
    exception_code: E004
  next_state:
    success: IN_DEV
    fail: DEV_BLOCKED
```

### NODE-P15-010 Python SDK 闭环

```yaml
node:
  id: NODE-P15-010
  name: Python 实盘 SDK 闭环
  pre_state: IN_DEV
  trigger_event: "P15-002/009.done"
  executor: dev_agent + reviewer
  sla: 24h
  actions:
    - 实现 REST 下单、查单、撤单、持仓和账户查询
    - 支持用户头、角色头和幂等键
    - 增加超时、重试和异常映射
    - 增加 SDK 集成测试
  success_criteria:
    - SDK 完成 Mock 环境端到端闭环
    - 无伪造成功和伪造订单号
    - Python 覆盖率不低于 80%
  fail_handler:
    action: rollback
    target_state: IN_DEV
    exception_code: E005
  next_state:
    success: DEV_DONE
    fail: DEV_BLOCKED
```

---

## 4. 测试工作流节点

### NODE-P15-TEST-001 测试准入

**前置**

- `DEV_DONE` 已上报。
- 单元测试和静态检查通过。
- 集成测试环境可自动部署。
- 开发和测试 schema 一致。

**动作**

- 启动 PG、ClickHouse、Kafka、Redis。
- 执行全部迁移脚本。
- 构建并启动核心服务。
- 执行健康检查和冒烟。

**失败处理**

- 环境失败：`TEST_BLOCKED`，异常码 E003。
- schema 失败：`TEST_BLOCKED`，异常码 E008。

### NODE-P15-TEST-002 功能与回归

**动作**

- 执行 Java 单元测试。
- 执行 Python 单元测试。
- 执行 Testcontainers 集成测试。
- 执行并发幂等测试。
- 执行重复、乱序和部分成交回报测试。
- 执行重启恢复和对账测试。
- 执行权限矩阵测试。
- 执行 Python SDK E2E。

**通过条件**

- 用例通过率 100%。
- 无未闭环高等级缺陷。
- 覆盖率门槛满足。

### NODE-P15-TEST-003 缺陷闭环

| 缺陷等级 | 处理策略 |
|----------|----------|
| 阻断 | 立即停止测试，回退开发 |
| 严重 | 进入 BUG_FIXING，修复后全量回归 |
| 一般 | 本阶段内闭环 |
| 轻微 | 可经人工确认后进入后续迭代 |

---

## 5. 一致性校验节点

### 实时校验

| 触发 | 校验 |
|------|------|
| 数据迁移提交 | schema、索引、默认值、旧数据兼容 |
| 交易接口提交 | 接口文档、错误码、状态机 |
| 权限提交 | 角色矩阵、敏感操作和审计 |
| SDK 提交 | REST 契约和错误映射 |

### 发布前全量校验

| 维度 | 内容 | 阻断 |
|------|------|------|
| 代码 | 构建、Lint、覆盖率、依赖、License、敏感信息 | 是 |
| 环境 | JDK、中间件版本、Compose、环境变量、健康检查 | 是 |
| 数据 | 表结构、索引、字典、初始化、对账 | 是 |
| 文档 | README、架构、接口、任务、上下文、交付报告 | 是 |

---

## 6. 事件路由与自动跟进

| 事件 | 来源 | 动作 | 下一状态 |
|------|------|------|----------|
| 计划确认 | 用户 | 初始化基线并分配任务 | IN_DEV |
| P15 任务完成 | 开发记录 | 更新任务卡和上下文 | 下一依赖任务 |
| 开发完成 | 全部必须任务完成 | 执行开发准出校验 | GLOBAL_IN_TEST |
| 测试通过 | 测试报告 | 执行一致性校验 | GLOBAL_IN_CONSISTENCY |
| 一致性通过 | 巡检报告 | 生成交付报告 | GLOBAL_DONE |
| 节点超时 | 定时巡检 | 按 2/8/24/48 小时升级 | 保持当前状态 |
| 校验失败 | 校验节点 | 回退并建整改项 | 上一稳定状态 |

---

## 7. SLA 与升级策略

| 级别 | 超时 | 通知对象 | 动作 |
|------|------|----------|------|
| 一级 | 2h | 任务负责人 | 在任务卡记录提醒 |
| 二级 | 8h | 项目负责人 | 纳入风险列表 |
| 三级 | 24h | 项目风险台账 | 暂停后续依赖任务 |
| 四级 | 48h | 阶段风险 | 评估计划调整 |

当前阶段按自然小时计算。由于是公开版半自动化模式，通知由 Codex 线程消息和 `.workflow` 状态更新完成。

---

## 8. 异常码映射

| 异常码 | 场景 | 自动处理 |
|--------|------|----------|
| E001 | 网络或容器启动超时 | 重试 3 次，指数退避 |
| E002 | Maven/Python 依赖下载失败 | 切换镜像或重新解析 |
| E003 | 环境部署失败 | 回滚镜像或重建中间件 |
| E004 | 静态扫描或安全检查阻断 | 生成修复项并驳回提交 |
| E005 | 单元或集成测试失败 | 记录用例，进入缺陷闭环 |
| E006 | 评审超时 | 按 SLA 升级 |
| E007 | 配置不一致 | 同步配置，关键项人工确认 |
| E008 | Schema 不一致 | 阻断发布，生成迁移草案 |
| E009 | 需求歧义 | 暂停任务，记录开放问题 |
| E010 | Git 或权限不足 | 人工处理，恢复后断点续跑 |

---

## 9. 当前基线

### 已验证

- Java `mvn -B clean verify`：成功。
- Java 测试：48/48 通过。
- Python 测试：14/14 通过。
- Python 覆盖率：65%。

### 未满足

- Git 仓库不存在。
- Docker daemon 未运行。
- 核心服务未运行。
- Java 覆盖率插件缺失。
- Python 覆盖率门槛未设置。
- 集成测试缺失。

### 结论

当前满足“需求规划准入”，不满足“开发任务流转 IN_DEV”的全部条件。首个必须动作是完成 P15-000。

---

## 10. 第一轮执行顺序

1. 人工确认需求文档与开放问题。
2. 执行 P15-000：Git 基线、任务状态和文档同步。
3. 执行 P15-001：数据库迁移。
4. 并行执行 P15-008 和 P15-011 基础部分。
5. 进入 P15-002 统一订单编排。

在 P15-000 完成前，不开始 P15-002 及之后的业务编码。

---

## 11. 交付物清单

| 交付物 | 路径 |
|--------|------|
| 详细需求规格 | `docs/p1.5-trade-closure-requirements.md` |
| 任务看板 | `.workflow/tasks-p15.md` |
| 工作流计划 | `.workflow/workflow-plan-p15.md` |
| 全局上下文 | `.workflow/context.yaml` |
| 测试计划 | `.workflow/test-plan-p15.md` |
| 测试报告 | `.workflow/test-report-p15.md` |
| 一致性报告 | `.workflow/consistency-report-p15.md` |
| 交付报告 | `.workflow/delivery-report-p15.md` |
