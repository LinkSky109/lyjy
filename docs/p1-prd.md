# 企业级量化交易平台（lhjypt）— P1 二期完整开发规范
# Claude Code 开发规范文档 v1.0

**文档版本**: v1.0
**创建日期**: 2026-10-02
**项目代号**: lhjypt
**状态**: 已确认需求，可直接进入开发
**开发工具**: Claude Code
**PRD Generator版本**: v4.0
**上游基线**: P0 一期交付完毕（GLOBAL_DONE）

> **本文档是 Claude Code 开发 P1 二期的唯一真相源。**
> P0 一期代码基线：Java 17 + Spring Boot 3.2.5 + MyBatis-Plus 3.5.5 + PostgreSQL + ClickHouse + Kafka，单 core 模块，端口 8081。
> 本文档全部设计在该基线上增量扩展，**不引入新中间件、不拆微服务**。

---

## 1. 项目概述与目标

### 1.1 项目背景（为什么做）

P0 一期已跑通单市场基础闭环：行情接入（Level-1/2 采集与存储）→ 数据治理（清洗/复权/统一模型）→ tick 级回测引擎 → Python 策略 SDK → CTP 交易柜台接入（Mock）→ OMS 订单管理（幂等+状态机）→ 事前风控引擎 → 基础资金管理（冻结/解冻/透支控制）。

P0 遗留的机构化能力缺口：
1. **交易链路**：仅单柜台（MockCTP），无多柜台多通道、无通道故障切换；
2. **风控纵深**：仅事前静态规则，无实时持仓/资金监控、无自动处置、无 VaR 度量、无异常交易行为识别；
3. **观测能力**：全链路无延迟/资源/业务指标监控，无告警体系，无容量预警与降级预案；
4. **资金纵深**：无日终清算对账批量流程、无多级对账与差异工单；
5. **组合与策略治理**：无组合定义/再平衡，策略源码明文落盘、无沙箱隔离。

P1 二期目标即补齐上述机构化能力，形成"可观测、可处置、可恢复、可审计"的机构级交易平台。

### 1.2 V1核心目标（一句话定位 + 量化指标）

> **P1 二期让 lhjypt 从"能跑通的单柜台原型"升级为"可观测、可风控、可对账、可降级的机构化交易平台"。**

对齐 `requirements-supplement.md` §5.1 容量表，P1 目标（本地环境以 Mock 数据验证逻辑，生产以真实依赖渐进替换）：

| 指标 | P1 目标 | 本地验证基线 |
|------|--------|-------------|
| 并发运行策略数 | 200 | 20（Mock 行情源） |
| 每秒订单处理 TPS | 5000 | 500（单机本地压测） |
| 同时持仓标的数 | 2000 | 200 |
| 行情订阅标的数 | 5000 | 1000 |
| 管理账户数 | 100 | 10 |
| 日终清算账户数 | 100 | 10 |
| 风控校验时间 | < 1ms | < 1ms（实测） |
| 订单响应 P99 | < 50ms | 本地实测记录基线 |
| 异常交易检测延迟 | < 100ms | < 100ms（事件流式检测） |
| 日终清算批处理时间 | < 30 分钟 | < 1 分钟（千行级数据） |
| 可用性 | 99.95% | 降级预案演练通过 |

### 1.3 目标用户（角色表）

| 角色 | role 字段值 | 特征 | 核心需求 |
|------|------------|------|----------|
| 系统管理员 | ADMIN | 平台部署运维、配置管理 | 网关/规则/告警/降级配置、系统监控 |
| 风控经理 | RISK_OFFICER | 风险规则与处置负责人 | 风控规则、异常交易、VaR、处置审批 |
| 交易员/策略师 | TRADER | 策略运行与交易执行 | 多柜台下单、组合管理、监控告警、日终报表 |
| 资金/清算专员 | FUND_MANAGER | 资金与日终结算 | 入出金、日终清算、对账差异处理 |
| 审计/合规 | COMPLIANCE | 审计追踪与合规 | 策略审计日志、异常交易报告、操作留痕 |
| 只读观察者 | VIEWER | 领导/外部只读 | 仪表盘、报表、只读查询 |

> P0 无用户体系，本文档引入轻量鉴权（见 §3），**不引入 OAuth2 服务端**，本地以 `X-User-Role` 头模拟（生产接统一 SSO，见 §9.3）。

### 1.4 架构设计要点

- 单租户（P0 基线，多租户隔离属 P2）。
- 单服务部署：新增能力全部落在 `core` 模块服务包内，V1 不拆微服务；模块间通过 Spring 事件 / Kafka topic 解耦。
- 事件驱动：交易/风控/清算/监控通过 Kafka topic 与 Spring ApplicationEvent 联动（P0 已验证 Kafka 行情分发）。
- 时序数据：监控指标与异常事件入 ClickHouse（已有 lhjypt 库），业务主数据入 PostgreSQL。
- 韧性优先：每个新模块必须实现"本地可运行 + 生产可替换"双态（接口抽象 + Mock 实现 + 生产适配器预留）。

---

## 2. 技术架构

### 2.1 技术栈（延续 P0，无新增中间件）

| 层 | 选型 | 版本（P0 基线） | 说明 |
|----|------|-----------------|------|
| 语言 | Java | 17 | 延续 |
| 框架 | Spring Boot | 3.2.5 | 延续 |
| ORM | MyBatis-Plus | 3.5.5 | 延续 |
| 主数据库 | PostgreSQL | 14+（Docker） | 业务主数据 |
| 时序数据库 | ClickHouse | 23.x（Docker） | 行情/指标/事件 |
| 消息队列 | Kafka | 3.7.0 | 事件驱动 |
| 缓存/限流 | Redis | 7.x（Docker） | P1 新增使用（限流桶、指标缓冲） |
| 加密 | JDK JCE + BouncyCastle(可选) | JDK17 内置 AES-GCM | 策略加密 |
| JSON | fastjson2 | 2.0.49 | 延续 |
| 测试 | JUnit5 + spring-boot-starter-test | 3.2.5 | 延续 |
| Python SDK | Python | 3.10+ | 策略侧延续 |

**新增 Java 依赖（仅列入 pom，版本随 Spring Boot BOM 管理）**：
- `spring-boot-starter-actuator`（健康/指标，监控告警数据源）
- `spring-boot-starter-data-redis`（限流/降级开关/指标缓冲）
- `resilience4j-spring-boot3`（熔断/限流/重试，性能降级）
- 不引入：SPRING SECURITY、完整 ELK/Prometheus 栈、XXL-JOB 等重量级组件（本地可运行优先，生产替换方案见 §14）。

### 2.2 技术选型理由

| 决策 | 理由 |
|------|------|
| 延续 Spring Boot 3.2.5 + Java 17 | 与 P0 完全一致，零迁移成本；P0 43 端点已验证可用 |
| 不拆微服务 | P1 目标是能力增量而非架构演进；单服务降低本地可运行成本，模块边界用包结构+事件隔离 |
| Resilience4j 做熔断/限流 | 轻量、Spring Boot 3 原生支持、可配置化，满足性能降级与第三方保护，无需引入 Hystrix |
| Redis 做限流/降级开关 | P0 compose 已有 Redis 5 容器（ZK/Kafka/PG/CH/Redis），零新增基础设施；限流桶与降级开关可热更新 |
| Micrometer + Actuator | Spring Boot 原生指标出口，本地可读 /actuator/metrics，生产可桥接 Prometheus，双态成本最低 |
| AES-256-GCM 策略加密 | JDK 内置，无需额外依赖即可满足"策略源码加密存储"；HSM/KMS 生产替换预留 SPI |
| 事件流式异常检测 | 异常交易监控需毫秒级，Kafka 消费委托/成交事件流式评分，避免批量扫描延迟 |

### 2.3 系统架构图（P1 增量）

```
                        ┌─────────────────────────────────────────────┐
                        │             lhjypt-core (8081)              │
                        │                                             │
 行情源(Mock/CTP MD) ──▶│  行情接入层(MarketData*)      [P0]        │
                        │        │                                    │
                        │  数据治理(DataGovernance*)     [P0]        │
                        │        │    行情Tick/Bar(Kafka)            │
                        │        ▼                                    │
 策略SDK(Python) ──────▶│  交易执行服务(TradeExecution*) [P0]        │
   │                    │        │                                    │
   │   ┌────────────────┼────────▼──────────────┐                     │
   │   │  【P1】多柜台多通道 GatewayRouter        │                     │
   │   │  ┌──────────┐ ┌──────────┐ ┌──────────┐ │                     │
   │   │  │CTPGateway│ │MockGate1 │ │MockGate2 │ │ 故障切换/路由规则     │
   │   │  └──────────┘ └──────────┘ └──────────┘ │                     │
   │   └────────────────┬────────────────────────┘                     │
   │                    ▼                                             │
   │   ┌────────────────────────────────────────────────────────┐     │
   │   │  OMS(OrderManagementService) [P0]                      │     │
   │   │  事前风控(PreTradeRiskService) [P0]                    │     │
   │   │  【P1】事中风控(IntradayRiskService) ◀── 事件/定时监控    │     │
   │   │  【P1】异常交易监控(AbnormalTradeMonitor) ◀── 委托/成交流 │     │
   │   └────────────────────────────────────────────────────────┘     │
   │                    │                                              │
   │   ┌────────────────▼────────────────────────┐                     │
   │   │ 资金管理(FundManagementService) [P0]    │                     │
   │   │ 【P1】日终清算对账(DailySettlementService)│                    │
   │   └─────────────────────────────────────────┘                     │
   │        ▲                  ▲        ▲                              │
   │   ┌────┴─────┐     ┌──────┴────┐  ┌┴──────────────┐               │
   │   │ 组合管理  │     │ VaR计算    │  │ 监控告警       │               │
   │   │ (P1)     │     │ (P1)      │  │ (P1)          │               │
   │   └──────────┘     └───────────┘  └───────────────┘               │
   │   ┌──────────────────────────────┐                                │
   │   │ 策略加密沙箱 StrategySandbox  │ 加载→解密→隔离执行→审计         │
   │   └──────────────────────────────┘                                │
   └───────────────────────────────────────────────────────────────────┘
        │        │        │        │        │
        ▼        ▼        ▼        ▼        ▼
   PostgreSQL  ClickHouse  Kafka   Redis  Actuator
   (主数据)    (行情/指标) (事件)   (限流)   (健康/指标)
```

**组件职责**：
- `GatewayRouter`（新增）：多柜台注册、健康检查、按账户/标的/优先级路由、故障切换（主→备）、路由规则热更新。
- `IntradayRiskService`（新增）：持仓/资金/订单流实时监控，超阈触发"预警→限制开仓→强制平仓"处置链。
- `AbnormalTradeMonitor`（新增）：Kafka 消费委托/成交/撤单事件，按规则评分（自成交/频繁报撤/虚假申报/拉抬打压），触发告警与处置。
- `AlertService`（新增）：统一告警入口，接收各模块告警事件，分级（P0/P1/P2），通知通道（钉钉/邮件/Webhook 预留，本地日志+回调）。
- `DailySettlementService`（新增）：日终批量清算（费用/盈亏/持仓核对）+ 多级对账（内部 vs 柜台/交易所）+ 差异工单。
- `PortfolioService`（新增）：组合定义、约束、再平衡触发与调仓指令生成。
- `VarCalculator`（新增）：历史模拟法 + 方差协方差法计算组合/账户 VaR。
- `StrategySecurityService`（新增）：策略源码 AES-256-GCM 加密存储、密钥管理（本地配置，生产 HSM/KMS 预留）、加载校验、审计。
- `StrategySandbox`（新增）：Python 策略隔离执行（资源限制+API 白名单+超时+异常隔离，容器沙箱预留）。
- `DegradeController`（新增）：降级开关注册表（每功能可降级）、过载保护（队列水位/并发）、熔断（Resilience4j）、行情源降级。

### 2.4 服务依赖关系图（启动顺序）

```
PostgreSQL / ClickHouse / Kafka / Redis（外部中间件，compose 先行）
        │
        ▼
lhjypt-core 启动（Spring Boot）
   │  1. 数据源与 MyBatis 初始化
   │  2. Kafka 监听器注册（行情、事件）
   │  3. GatewayRouter 初始化（注册 Mock/CTP 网关，异步连接）
   │  4. 监控告警组件启动（指标采集、规则加载）
   │  5. 清算调度器注册（@Scheduled，默认关闭触发）
   ▼
策略SDK（Python）连接 core（REST 8081）运行策略
```

### 2.5 有状态/无状态组件标注

| 组件 | 状态 | 说明 |
|------|------|------|
| PostgreSQL / ClickHouse / Kafka / Redis | 有状态（外部） | 由基础设施层保证 HA（P2 异地灾备） |
| GatewayRouter | 有状态（内存连接句柄） | 网关连接在内存；路由配置落 PG 可重建 |
| OrderService / FundService | 有状态（DB） | 无进程内状态，可水平扩展 |
| IntradayRisk 处置状态 | 有状态（DB+Redis） | 处置执行限流位在 Redis，可分布式 |
| Alert 规则与通知 | 无状态 | 规则在 PG，指标在 CH/内存环形缓冲 |
| 策略沙箱执行器 | 有状态（进程池） | 本机进程池执行；生产可替换容器调度 |
| VaR 计算 | 无状态 | 输入持仓+历史行情 → 输出 VaR，可并行 |

---

## 3. 权限体系

### 3.1 权限矩阵（角色 × P1 模块 × 数据范围）

> 数据范围：`SELF`=仅本人相关；`ALL`=全量。

| 功能模块 | ADMIN | RISK_OFFICER | TRADER | FUND_MANAGER | COMPLIANCE | VIEWER |
|----------|:-----:|:------------:|:------:|:------------:|:----------:|:------:|
| 多柜台网关：查看连接/路由 | 读写(ALL) | 只读(ALL) | 只读(SELF) | - | 只读(ALL) | 只读(ALL) |
| 多柜台网关：路由规则配置 | 读写(ALL) | 只读 | - | - | 只读 | - |
| 事中风控：规则配置 | 读写(ALL) | 读写(ALL) | 只读 | - | 只读 | - |
| 事中风控：处置执行（强平） | 确认后执行 | 确认后执行 | 只读 | - | 只读 | 只读 |
| VaR：查看 | 读写 | 读写 | 只读(SELF) | 只读 | 只读 | 只读 |
| 监控告警：规则配置 | 读写 | 读写 | 只读 | 只读 | 只读 | 只读 |
| 监控告警：查看 | 读写 | 读写 | 只读(SELF) | 只读(SELF) | 只读 | 只读 |
| 组合管理：构建/再平衡 | 读写 | 只读 | 读写(SELF) | 只读 | 只读 | 只读 |
| 组合管理：再平衡执行 | 确认后执行 | 确认后执行 | 确认后执行(SELF) | - | 只读 | - |
| 日终清算：运行/重跑 | 执行 | - | - | 执行 | 只读 | - |
| 日终清算：差异处理 | 审批差异 | - | - | 处理差异 | 只读 | - |
| 策略加密：上传/下载 | 读写 | 只读 | 读写(SELF) | - | 只读 | - |
| 策略沙箱：启停 | 执行 | 只读 | 执行(SELF) | - | 只读 | - |
| 策略审计日志 | 只读 | 只读 | 只读(SELF) | - | 读写 | 只读 |
| 异常交易：监控查看 | 读写 | 读写 | 只读(SELF) | - | 读写 | 只读 |
| 异常交易：处置/上报 | 确认后执行 | 确认后执行 | - | - | 确认后执行 | - |
| 性能降级：开关切换 | 执行 | 只读 | - | - | 只读 | - |
| 全部 P0 既有接口 | 读写 | 按 P0 | 按 P0 | 按 P0 | 只读 | 只读 |

**"确认后执行"**：高风险操作（强平、再平衡执行、异常交易处置、降级开关）须带 `X-Confirm-Token`（短时一次性，见 §3.5）。

### 3.2 数据过滤规则

- `TRADER` 查询订单/持仓/策略/组合：`WHERE strategy_owner = 当前用户`（用户 ID 从 `X-User-Id` 头取值）。
- `FUND_MANAGER` 查询资金/清算：`WHERE account_group = 用户所属组`（`X-User-Group`）；未传组则仅本人创建记录。
- `VIEWER`：全局只读，拒绝一切写操作；写接口拦截返回 403。
- `COMPLIANCE`：审计模块全量读写，其余模块只读。
- 过滤逻辑统一在 `PermissionAspect`（AOP 切面）实现，禁止在 Controller 手写。

### 3.3 关键权限逻辑伪代码

```
拦截器 AuthInterceptor.preHandle:
    if (uri 命中 /api/** 且非 /actuator/health):
        role = header X-User-Role ?: "VIEWER"        // 本地模拟；生产接 SSO 后由网关注入
        uid  = header X-User-Id ?: ""
        PermissionAspect 在方法级校验:
            required = @RequiresRole(value={...}, confirm=false)
            if role 不在 required: return 403
            if method 带 confirm=true 且无有效 X-Confirm-Token: return 409(需二次确认)
            数据范围 = 角色对应用户/组过滤条件自动拼装到 Service 层查询
```

### 3.4 前端路由守卫规则（P1 无独立前端，提供管理接口）

P1 本期不交付独立前端页面（管理界面属 P2），监控/告警/清算以 REST API + 日志 + 简易 HTML 看板（`/admin/monitor.html` 静态页，只读）提供。该看板仅读取一次性 token（`/api/auth/viewer-token` 换），无写操作。

### 3.5 敏感操作二次确认清单

| 操作 | 二次确认方式 |
|------|-------------|
| 强制平仓执行 | 输入确认口令 `X-Confirm-Token`（POST /api/auth/confirm-token 获取，30s 有效一次） |
| 组合再平衡执行 | 同上 |
| 异常交易标记上报 | 同上 |
| 日终清算重跑 | 同上 |
| 降级开关切换 | 同上 |
| 策略源码下载 | 需要口令页 + 操作审计留痕 |

---

## 4. 数据模型

### 4.1 ER 关系图（P1 新增核心表，ASCII）

```
gateway_info(网关) 1──N gateway_route_rule(通道路由规则)
account 1──N strategy(策略) 1──N portfolio(组合) 1──N portfolio_position(组合持仓)
组合 ──N── portfolio_rebalance_job(再平衡任务)
风险规则 risk_rule(规则) ──N── risk_intercept_log(拦截日志已有)
异常交易 abnormal_trade_alert(异常交易告警)
清算 settlement_batch(批次) 1──N settlement_diff_item(对账差异工单)
监控 alert_rule(规则) 1──N alert_event(告警事件)
策略策略安全: strategy_crypto(加密策略) 1──N strategy_audit_log(策略审计)
Var: var_calculation(计算记录, 每次计算一行)
```

### 4.2 每张新增表的完整 DDL（追加到 `scripts/init-p1.sql`）

> **所有表统一约定（对齐 P0 `scripts/init-db.sql` 实际基线，避免新旧表混用）**：主键 `BIGSERIAL`、审计字段 `create_time/update_time`（TIMESTAMP DEFAULT CURRENT_TIMESTAMP）、逻辑删除 `deleted SMALLINT DEFAULT 0`；金额统一 `DECIMAL(20,8)`；枚举存字符串（大写）。
> 下方 DDL 为设计示意，**正式建表以 `scripts/init-p1.sql`（已按本约定落地）为准**。

```sql
-- ========== P1 新增表 ==========

-- 1. 网关注册表
CREATE TABLE IF NOT EXISTS gateway_info (
    id             BIGSERIAL PRIMARY KEY,
    gateway_id     VARCHAR(64)  NOT NULL UNIQUE,          -- 网关唯一标识，如 mock-g1/ctp-main
    gateway_type   VARCHAR(32)  NOT NULL,                 -- MOCK / CTP / FIX(预留)
    name           VARCHAR(128) NOT NULL,
    status         VARCHAR(16)  NOT NULL DEFAULT 'DOWN',  -- DOWN/CONNECTING/READY/DEGRADED
    param_config   JSONB        NOT NULL DEFAULT '{}',    -- 连接参数(前端/账户/密钥引用，不存明文)
    priority       INT          NOT NULL DEFAULT 100,     -- 路由优先级(小=高)
    health_path    VARCHAR(128),
    last_health_at TIMESTAMPTZ,
    description    VARCHAR(512),
    create_time    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    update_time    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted        INT          NOT NULL DEFAULT 0
);
CREATE INDEX idx_gateway_status ON gateway_info(status) WHERE deleted=0;

-- 2. 通道路由规则表
CREATE TABLE IF NOT EXISTS gateway_route_rule (
    id           BIGSERIAL PRIMARY KEY,
    rule_id      VARCHAR(64) NOT NULL UNIQUE,
    rule_name    VARCHAR(128) NOT NULL,
    account_id   VARCHAR(64),                 -- 空=全账户
    symbol_regex VARCHAR(128),                -- 标的匹配(空=全部)
    order_type   VARCHAR(16),                 -- 空=全部
    gateway_id   VARCHAR(64) NOT NULL,        -- 命中后路由到
    fallback_id  VARCHAR(64),                 -- 主网关故障时的备选
    priority     INT NOT NULL DEFAULT 100,
    enabled      BOOLEAN NOT NULL DEFAULT TRUE,
    create_time  TIMESTAMPTZ NOT NULL DEFAULT now(),
    update_time  TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted      INT NOT NULL DEFAULT 0
);
CREATE INDEX idx_route_enabled ON gateway_route_rule(enabled) WHERE enabled AND deleted=0;

-- 3. 策略注册表（扩展 P0 无策略表，新增）
CREATE TABLE IF NOT EXISTS strategy_info (
    id           BIGSERIAL PRIMARY KEY,
    strategy_id  VARCHAR(64) NOT NULL UNIQUE,
    name         VARCHAR(128) NOT NULL,
    owner        VARCHAR(64) NOT NULL,                 -- 策略作者/归属用户
    status       VARCHAR(16) NOT NULL DEFAULT 'OFFLINE', -- OFFLINE/LOADING/RUNNING/PAUSED/ERROR
    lang         VARCHAR(16) NOT NULL DEFAULT 'PYTHON',
    risk_profile VARCHAR(64),                          -- 关联风险预算配置(预留)
    params_json  JSONB NOT NULL DEFAULT '{}',
    create_time  TIMESTAMPTZ NOT NULL DEFAULT now(),
    update_time  TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted      INT NOT NULL DEFAULT 0
);
CREATE INDEX idx_strategy_owner ON strategy_info(owner) WHERE deleted=0;

-- 4. 组合定义表
CREATE TABLE IF NOT EXISTS portfolio (
    id              BIGSERIAL PRIMARY KEY,
    portfolio_id    VARCHAR(64) NOT NULL UNIQUE,
    name            VARCHAR(128) NOT NULL,
    owner           VARCHAR(64) NOT NULL,
    status          VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',  -- ACTIVE/PAUSED/CLOSED
    base_currency   VARCHAR(8)  NOT NULL DEFAULT 'CNY',
    config_json     JSONB NOT NULL DEFAULT '{}',          -- 约束/目标/再平衡配置(见4.3.1)
    create_time     TIMESTAMPTZ NOT NULL DEFAULT now(),
    update_time     TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted         INT NOT NULL DEFAULT 0
);
CREATE INDEX idx_portfolio_owner ON portfolio(owner) WHERE deleted=0;

-- 5. 组合持仓表
CREATE TABLE IF NOT EXISTS portfolio_position (
    id            BIGSERIAL PRIMARY KEY,
    portfolio_id  VARCHAR(64) NOT NULL,
    symbol        VARCHAR(32) NOT NULL,
    exchange      VARCHAR(16),
    direction     VARCHAR(8)  NOT NULL DEFAULT 'LONG',    -- LONG/SHORT
    target_qty    NUMERIC(20,4) NOT NULL DEFAULT 0,       -- 目标持仓
    cur_qty       NUMERIC(20,4) NOT NULL DEFAULT 0,       -- 当前持仓
    weight_limit  NUMERIC(8,6),                           -- 个股仓位上限(0~1)
    update_time   TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(portfolio_id, symbol, direction)
);

-- 6. 再平衡任务表
CREATE TABLE IF NOT EXISTS portfolio_rebalance_job (
    id             BIGSERIAL PRIMARY KEY,
    job_id         VARCHAR(64) NOT NULL UNIQUE,
    portfolio_id   VARCHAR(64) NOT NULL,
    trigger_type   VARCHAR(16) NOT NULL,                  -- SCHEDULED/THRESHOLD/EVENT/MANUAL
    status         VARCHAR(16) NOT NULL DEFAULT 'PENDING',-- PENDING/PREVIEWED/APPROVED/EXECUTING/DONE/FAILED/CANCELED
    preview_json   JSONB,                                 -- 调仓指令预览(见4.3.2)
    execute_mode   VARCHAR(16) NOT NULL DEFAULT 'MANUAL', -- AUTO/MANUAL
    diff_total     NUMERIC(20,4),
    cost_est       NUMERIC(20,4),
    executed_at    TIMESTAMPTZ,
    create_time    TIMESTAMPTZ NOT NULL DEFAULT now(),
    update_time    TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted        INT NOT NULL DEFAULT 0
);
CREATE INDEX idx_rb_portfolio ON portfolio_rebalance_job(portfolio_id, status);

-- 7. 事中风控规则表（复用风险规则概念，新增类型字段）
CREATE TABLE IF NOT EXISTS intraday_risk_rule (
    id            BIGSERIAL PRIMARY KEY,
    rule_id       VARCHAR(64) NOT NULL UNIQUE,
    name          VARCHAR(128) NOT NULL,
    rule_type     VARCHAR(32) NOT NULL,   -- POSITION_LIMIT/LOSS_LIMIT/ORDER_RATE/VAR_LIMIT/ABNORMAL_RATE
    scope_json    JSONB NOT NULL DEFAULT '{}',   -- 作用范围: 账户/策略/标的
    threshold     JSONB NOT NULL DEFAULT '{}',   -- 阈值参数
    action_level  VARCHAR(16) NOT NULL DEFAULT 'WARN', -- WARN/BLOCK/FORCE_CLOSE
    enabled       BOOLEAN NOT NULL DEFAULT TRUE,
    create_time   TIMESTAMPTZ NOT NULL DEFAULT now(),
    update_time   TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted       INT NOT NULL DEFAULT 0
);
CREATE INDEX idx_intraday_rule_enabled ON intraday_risk_rule(enabled) WHERE enabled AND deleted=0;

-- 8. 处置执行记录
CREATE TABLE IF NOT EXISTS risk_disposition_log (
    id            BIGSERIAL PRIMARY KEY,
    disposition_id VARCHAR(64) NOT NULL UNIQUE,
    trigger_rule  VARCHAR(64) NOT NULL,
    scope_type    VARCHAR(16) NOT NULL,      -- ACCOUNT/STRATEGY/SYMBOL
    scope_id      VARCHAR(64) NOT NULL,
    action        VARCHAR(32) NOT NULL,      -- WARN/BLOCK_OPEN/FORCE_CLOSE/THROTTLE
    params_json   JSONB,
    result        VARCHAR(32),               -- PENDING/SUCCESS/FAILED/REJECTED
    confirm_by    VARCHAR(64),
    confirm_token VARCHAR(128),              -- 预留关联
    create_time   TIMESTAMPTZ NOT NULL DEFAULT now(),
    update_time   TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted       INT NOT NULL DEFAULT 0
);
CREATE INDEX idx_disposition_scope ON risk_disposition_log(scope_type, scope_id, create_time);

-- 9. VaR 计算记录
CREATE TABLE IF NOT EXISTS var_calculation (
    id            BIGSERIAL PRIMARY KEY,
    calc_id       VARCHAR(64) NOT NULL UNIQUE,
    scope_type    VARCHAR(16) NOT NULL,      -- PORTFOLIO/ACCOUNT/STRATEGY
    scope_id      VARCHAR(64) NOT NULL,
    method        VARCHAR(24) NOT NULL,      -- HISTORICAL/VAR_COV/MONTE_CARLO(预留)
    confidence    NUMERIC(6,4) NOT NULL DEFAULT 0.95,
    horizon_days  INT NOT NULL DEFAULT 1,
    var_value     NUMERIC(20,4) NOT NULL,    -- 金额(正数表示可能损失)
    pnl_hist_json JSONB,                     -- 损益序列概要(预留)
    calc_time     TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(scope_type, scope_id, calc_time)
);
CREATE INDEX idx_var_scope ON var_calculation(scope_type, scope_id, calc_time DESC);

-- 10. 异常交易告警
CREATE TABLE IF NOT EXISTS abnormal_trade_alert (
    id             BIGSERIAL PRIMARY KEY,
    alert_id       VARCHAR(64) NOT NULL UNIQUE,
    rule_code      VARCHAR(32) NOT NULL,   -- SELF_TRADE/FREQ_CANCEL/FALSE_ORDER/PUMP_DUMP/...
    account_id     VARCHAR(64),
    strategy_id    VARCHAR(64),
    symbol         VARCHAR(32),
    score          NUMERIC(8,4) NOT NULL,
    detail_json    JSONB NOT NULL DEFAULT '{}',   -- 事件明细(时间窗内订单/成交)
    severity       VARCHAR(8)  NOT NULL DEFAULT 'INFO', -- INFO/WARN/CRITICAL
    status         VARCHAR(16) NOT NULL DEFAULT 'OPEN', -- OPEN/CONFIRMING/REPORTED/CLOSED
    handler        VARCHAR(64),
    handle_note    VARCHAR(512),
    create_time    TIMESTAMPTZ NOT NULL DEFAULT now(),
    update_time    TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted        INT NOT NULL DEFAULT 0
);
CREATE INDEX idx_alert_status ON abnormal_trade_alert(status, severity, create_time DESC);

-- 11. 告警规则表
CREATE TABLE IF NOT EXISTS alert_rule (
    id           BIGSERIAL PRIMARY KEY,
    rule_id      VARCHAR(64) NOT NULL UNIQUE,
    name         VARCHAR(128) NOT NULL,
    metric_key   VARCHAR(64) NOT NULL,      -- 指标键(system.cpu/delay.p99/lost.rate/strategy.error/...)
    condition    VARCHAR(16) NOT NULL,      -- GT/LT/GTE/LTE/HAS
    threshold    VARCHAR(64) NOT NULL,
    window_sec   INT NOT NULL DEFAULT 60,
    severity     VARCHAR(8) NOT NULL DEFAULT 'WARN',
    channels_json JSONB NOT NULL DEFAULT '["log"]',  -- log/email/dingtalk/webhook(预留)
    enabled      BOOLEAN NOT NULL DEFAULT TRUE,
    create_time  TIMESTAMPTZ NOT NULL DEFAULT now(),
    update_time  TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted      INT NOT NULL DEFAULT 0
);
CREATE INDEX idx_alert_rule_enabled ON alert_rule(enabled) WHERE enabled AND deleted=0;

-- 12. 告警事件表
CREATE TABLE IF NOT EXISTS alert_event (
    id            BIGSERIAL PRIMARY KEY,
    event_id      VARCHAR(64) NOT NULL UNIQUE,
    rule_id       VARCHAR(64),
    metric_key    VARCHAR(64) NOT NULL,
    value         VARCHAR(64) NOT NULL,
    severity      VARCHAR(8) NOT NULL,
    message       VARCHAR(512),
    status        VARCHAR(16) NOT NULL DEFAULT 'OPEN', -- OPEN/ACKED/CLOSED
    ack_by        VARCHAR(64),
    ack_time      TIMESTAMPTZ,
    create_time   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_alert_event_status ON alert_event(status, severity, create_time DESC);

-- 13. 策略加密表
CREATE TABLE IF NOT EXISTS strategy_crypto (
    id             BIGSERIAL PRIMARY KEY,
    strategy_id    VARCHAR(64) NOT NULL UNIQUE,
    version        INT NOT NULL DEFAULT 1,
    algo           VARCHAR(16) NOT NULL DEFAULT 'AES_256_GCM',
    ciphertext     TEXT NOT NULL,                -- 密文(Base64)
    key_id         VARCHAR(64) NOT NULL,          -- 密钥引用(本地key-id，生产KMS key-id)
    checksum       VARCHAR(64) NOT NULL,          -- 源码SHA-256，防篡改
    uploaded_by    VARCHAR(64),
    create_time    TIMESTAMPTZ NOT NULL DEFAULT now(),
    update_time    TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted        INT NOT NULL DEFAULT 0
);
CREATE INDEX idx_crypto_strategy ON strategy_crypto(strategy_id, version);

-- 14. 策略操作审计日志
CREATE TABLE IF NOT EXISTS strategy_audit_log (
    id            BIGSERIAL PRIMARY KEY,
    strategy_id   VARCHAR(64) NOT NULL,
    action        VARCHAR(32) NOT NULL,   -- UPLOAD/DOWNLOAD/RUN/STOP/EDIT_PARAM/EXECUTE_CALL/...
    operator      VARCHAR(64) NOT NULL,
    detail_json   JSONB NOT NULL DEFAULT '{}',
    ip_addr       VARCHAR(64),
    create_time   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_audit_strategy ON strategy_audit_log(strategy_id, create_time DESC);

-- 15. 清算批次表
CREATE TABLE IF NOT EXISTS settlement_batch (
    id             BIGSERIAL PRIMARY KEY,
    batch_id       VARCHAR(64) NOT NULL UNIQUE,
    trade_date     DATE NOT NULL,
    status         VARCHAR(16) NOT NULL DEFAULT 'CREATED', -- CREATED/RUNNING/REVIEWING/CLOSED/FAILED
    account_count  INT NOT NULL DEFAULT 0,
    total_pnl      NUMERIC(20,4),
    total_fee      NUMERIC(20,4),
    total_diff     INT NOT NULL DEFAULT 0,
    started_at     TIMESTAMPTZ,
    finished_at    TIMESTAMPTZ,
    error_message  VARCHAR(512),
    create_time    TIMESTAMPTZ NOT NULL DEFAULT now(),
    update_time    TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(trade_date)
);

-- 16. 对账差异工单表
CREATE TABLE IF NOT EXISTS settlement_diff_item (
    id            BIGSERIAL PRIMARY KEY,
    diff_id       VARCHAR(64) NOT NULL UNIQUE,
    batch_id      VARCHAR(64) NOT NULL REFERENCES settlement_batch(batch_id),
    account_id    VARCHAR(64) NOT NULL,
    field_name    VARCHAR(64) NOT NULL,   -- available/frozen/margin/position_xxx
    system_value  NUMERIC(20,4),
    counter_value NUMERIC(20,4),
    diff_value    NUMERIC(20,4),
    tolerance     NUMERIC(20,4) NOT NULL DEFAULT 0,
    status        VARCHAR(16) NOT NULL DEFAULT 'OPEN', -- OPEN/ADJUSTING/ADJUSTED/CLOSED
    adjust_by     VARCHAR(64),
    adjust_note   VARCHAR(512),
    create_time   TIMESTAMPTZ NOT NULL DEFAULT now(),
    update_time   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_diff_batch ON settlement_diff_item(batch_id, status);

-- 17. 性能降级开关表
CREATE TABLE IF NOT EXISTS degrade_switch (
    id          BIGSERIAL PRIMARY KEY,
    switch_key  VARCHAR(64) NOT NULL UNIQUE,  -- 如 degrade.risk.v1 / strategy.thirdparty
    label       VARCHAR(128),
    level       VARCHAR(16) NOT NULL DEFAULT 'OFF',  -- OFF/ON_MAIN/ON_BACKUP
    operator    VARCHAR(64),
    reason      VARCHAR(256),
    create_time TIMESTAMPTZ NOT NULL DEFAULT now(),
    update_time TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted     INT NOT NULL DEFAULT 0
);
```

### 4.3 JSON 字段结构示例

**4.3.1 portfolio.config_json**

```json
{
  "constraints": {
    "single_symbol_max": 0.20,
    "industry_max": 0.35,
    "total_leverage_max": 1.0,
    "min_liquidity_cny": 5000000
  },
  "objective": {"mode": "absolute_return", "target_ann_return": 0.15},
  "rebalance": {
    "scheduled": {"cron": "0 30 15 * * 1-5"},
    "threshold": {"deviation_pct": 0.05},
    "event_driven": {"signals": ["risk_budget_breach"]}
  },
  "risk_budget": {"max_drawdown": 0.1, "max_var_95_1d": 100000}
}
```

**4.3.2 再平衡指令预览 preview_json**

```json
{
  "instructions": [
    {"symbol": "rb2410", "direction": "BUY", "qty": 5, "price_limit": null,
     "reason": "权重偏离3.2% > 阈值2%", "est_cost": 12.5}
  ],
  "summary": {"trade_count": 3, "est_cost": 38.2, "est_turnover": 1250000}
}
```

**4.3.3 异常交易 alert.detail_json**

```json
{
  "window": {"start": "2026-10-02T09:35:00+08:00", "end": "2026-10-02T09:40:00+08:00"},
  "orders": [{"order_id": "O202610020935000001", "action": "CANCEL", "ts": "2026-10-02T09:35:12+08:00"}],
  "metrics": {"cancel_rate": 0.62, "self_trade_hit": 2},
  "evidence": "5分钟内撤单62%，成交仅2笔"
}
```

### 4.4 索引清单

以上 DDL 已内联创建索引（`idx_*`）。补充组合表常用查询索引：

```sql
CREATE INDEX idx_port_position_pf ON portfolio_position(portfolio_id);
CREATE INDEX idx_gateway_route_acc ON gateway_route_rule(account_id) WHERE deleted=0;
CREATE INDEX idx_ch_tick_symbol_time ON lhjypt.tick(symbol, ts);  -- 若已有则跳过
```

### 4.5 数据库迁移策略

- P1 新增表集中在 `scripts/init-p1.sql`，一次性幂等脚本（`IF NOT EXISTS`），执行方式与 P0 一致（启动时检测缺表自动执行，或手动 psql 执行）。
- **不引入 Flyway**（保持 P0 简单基线）；每次 P1 迭代只往 `init-p1.sql` 追加 `IF NOT EXISTS` 段，保证可重放。

### 4.6 大表迁移注意事项

- 本地数据量小，无在线 DDL 需求；预留说明：生产环境 `abnormal_trade_alert`/`alert_event` 若超千万行，按 `create_time` 月表分区（预留未实施）。

### 4.7 数据增长预估与归档策略

| 表 | 月增长（本地 Mock） | 归档策略 |
|----|-------------------|----------|
| alert_event | ~数千行 | 保留 90 天，超期转 ClickHouse 归档（预留） |
| abnormal_trade_alert | ~数百行 | 保留 1 年（合规要求异常交易报告留痕） |
| strategy_audit_log | ~数百行 | 保留 ≥3 年（P1 准出硬性要求） |
| var_calculation | 每账户×每日 1 行 | 保留 180 天 |
| settlement_batch/diff | 每交易日少量 | 永久保留（清算对账审计） |

---

## 5. 功能模块详细说明

> 每个模块均遵循统一骨架：
> 正常路径 → 异常处理 → 异步/并发 → 数据流向 → 幂等 → 事件联动。

### 5.1 多柜台多通道（GatewayRouter）

**功能描述**：统一交易网关路由层，把"单一 Mock CTP"升级为"多网关注册 + 智能路由 + 故障切换"。交易链路（TradeExecutionService → OMS）不再直连单个网关，而是通过 `GatewayRouter` 按路由规则分发到目标网关。

**业务规则**：
- 网关注册：`POST /api/gateway/register` 注册 MOCK/CTP 网关，参数存 `gateway_info.param_config`（密钥字段只存引用，明文不入库）。
- 路由规则：按优先级匹配（匹配账户 → 标的 → 订单类型 → 兜底），`fallback_id` 指定主网关故障时的备选。
- 健康检查：每 30s 对 READY 网关探测（Mock 恒真，CTP 由适配器上报）；连续 3 次失败标记 DOWN 并告警，路由跳过 DOWN 网关。
- 故障切换：主网关无响应（超时 2s）→ 未成交订单自动路由 fallback 重试（限 1 次，防重复成交靠 OMS 幂等键）；切换事件发 Kafka `topic.gateway-switch`。
- 熔断：同一网关 5s 内错误率 > 50% 熔断 30s（Resilience4j），熔断期全量走 fallback。

**正常路径**：TradeExecutionService 收到报单 → GatewayRouter.chooseRoute(account, symbol, orderType) → 命中规则 → 目标网关接单 → 回报回调 → 状态回流 OMS。
**异常处理**：无可用网关 → 返回 `GATEWAY_UNAVAILABLE` 错误，订单回到 PENDING 并告警；用户看到"当前无可用交易通道"。
**数据流向**：TradeController → TradeExecutionService → GatewayRouter → TradingGateway 实现；配置读 `gateway_info`/`gateway_route_rule`。
**幂等**：报单幂等沿用 OMS `orderId`（P0 已有）；路由层不做二次幂等。
**并发控制**：网关连接句柄由各自实现内部管理；路由表读取加缓存（30s TTL），变更即刷新。

**API**：
- `POST /api/gateway/register` 注册网关（ADMIN）
- `POST /api/gateway/{gatewayId}/connect` 连接（ADMIN）
- `POST /api/gateway/{gatewayId}/disconnect` 断开（ADMIN）
- `GET /api/gateway/list` 网关列表与状态（ADMIN/TRADER 只读）
- `GET /api/gateway/{gatewayId}/health` 健康详情（ADMIN）
- `POST /api/gateway/route` 新建/更新路由规则（ADMIN）
- `GET /api/gateway/route/list` 路由规则列表（ADMIN）
- `POST /api/gateway/{gatewayId}/switch` 手动切换某账户流量到指定网关（ADMIN，需确认口令）

### 5.2 监控告警（AlertService + MetricsSampler）

**功能描述**：全链路可观测。采集系统资源（CPU/内存/磁盘/网络）、行情延迟、订单链路延迟（报单→回报）、风控耗时、策略异常率、失败率等指标，按规则判定告警，分级通知。

**业务规则**：
- 指标采集：Actuator `/actuator/metrics` + 自定义 `MeterRegistry` 仪表（延迟直方图、计数、水位）；每 10s 采样入 ClickHouse `metric_sample`（新表见下）。
- 告警规则：`alert_rule` 支持 metric+condition+threshold+window；评估线程每 15s 扫描窗口聚合值。
- 分级：P0(CRITICAL 全链路中断/资金异常) / P1(WARN 单模块异常) / P2(INFO 容量预警)。
- 通知通道：本地默认 `log` + 可配 webhook（钉钉群机器人预留）；生产可接 Prometheus Alertmanager（§14）。
- 告警事件落 `alert_event`，留存 90 天。

**正常路径**：指标采样 → 窗口聚合 → 规则评估 → 命中 → 生成 alert_event + 通知 + 关联降级建议。
**异常处理**：通知通道失败不影响系统；告警事件仍落库可查。
**数据流向**：Micrometer 指标 → MetricSampler → ClickHouse `metric_sample`；AlertEvaluator → PG `alert_rule` → `alert_event`。

补充表：

```sql
-- 指标采样表(ClickHouse)
CREATE TABLE IF NOT EXISTS lhjypt.metric_sample (
    ts        DateTime('Asia/Shanghai'),
    metric    String,
    value     Float64,
    labels    String   -- JSON: gateway/strategy/account
) ENGINE = MergeTree() ORDER BY (ts, metric);
```

**API**：
- `GET /api/monitor/metrics/latest` 最新指标快照（ADMIN/RISK/VIEWER）
- `GET /api/monitor/metrics/history?metric=&from=&to=` 历史指标（ADMIN/RISK）
- `POST /api/monitor/alert-rule` 创建/更新规则（ADMIN/RISK_OFFICER）
- `GET /api/monitor/alert-rule/list` 规则列表（ADMIN/RISK_OFFICER）
- `GET /api/monitor/alerts?status=` 告警事件列表（ADMIN/RISK）
- `POST /api/monitor/alerts/{eventId}/ack` 确认告警（ADMIN/RISK）
- `POST /api/monitor/alerts/{eventId}/close` 关闭告警（ADMIN/RISK）

### 5.3 事中风控（IntradayRiskService）

**功能描述**：在交易进行中对持仓、资金、订单流、VaR 做实时监控，突破"仅事前静态规则"的限制。产物为：规则配置、实时评估、处置执行（预警→限制开仓→强制平仓）。

**业务规则**：
- 规则类型：`POSITION_LIMIT`（持仓超限）、`LOSS_LIMIT`（日内亏损超限）、`ORDER_RATE`（下单频率）、`VAR_LIMIT`（VaR 超限）、`ABNORMAL_RATE`（异常率）。
- 评估触发：委托/成交流事件（Kafka）+ 定时（5s）扫描持仓/资金快照；风控校验时延目标 < 1ms（本地 Mock 数据）。
- 处置链：`WARN`（记日志+告警）→ `BLOCK`（拦截新下单，已挂单保留）→ `FORCE_CLOSE`（生成强平指令，需二次确认执行）。
- 处置留痕：`risk_disposition_log` 全记录，支持审计追溯。
- 与 P0 PreTradeRiskService 的关系：事前在报单入口校验静态规则；事中做动态实时监控；两者共用 `risk_intercept_log`（P0 已建）。

**正常路径**：成交流 → 持仓/资金更新 → 评估线程触发 → 命中规则 → 按 action_level 处置 → 记录 + 告警。
**异常处置**：评估线程自身异常不阻断交易链路（try-fail-log，降级为仅 WARN）。
**数据流向**：Kafka order/trade 事件 → IntradayRiskService → 规则评估 → disposition_log + alert_event；读取 `intraday_risk_rule`。
**并发**：同一账户同一规则 5s 内只触发一次处置（Redis 位 + TTL 去重），防止风暴。

**API**：
- `POST /api/risk/intraday/rule` 创建/更新事中规则（RISK_OFFICER）
- `GET /api/risk/intraday/rule/list` 规则列表（RISK/只读）
- `POST /api/risk/intraday/rule/{ruleId}/toggle` 启停（RISK_OFFICER）
- `GET /api/risk/intraday/status/{accountId}` 账户实时风控状态（持仓/亏损/VaR 占用）（RISK/TRADER 只读自属）
- `POST /api/risk/intraday/disposition/{dispositionId}/confirm` 确认处置（强平）执行（RISK_OFFICER，确认口令）
- `GET /api/risk/intraday/disposition/list` 处置记录（RISK/COMPLIANCE）

### 5.4 异常交易监控（AbnormalTradeMonitor）

**功能描述**：识别交易所监管规则中的异常行为（自成交、频繁报撤、虚假申报、拉抬打压），毫秒级检测并告警/限制。

**业务规则**：
- 事件流：消费 Kafka 委托/成交/撤单事件，按账户×策略做滑动窗口统计。
- 规则与评分：
  - `SELF_TRADE`：同一策略买卖盘撮合（自成交）→ 计分。
  - `FREQ_CANCEL`：撤单率 > 阈值（如 60%），5 分钟窗口。
  - `FALSE_ORDER`：大额申报快速撤单，报撤比异常。
  - `PUMP_DUMP`：同一标的价格快速拉升/砸盘（tick 级价格变动率）。
- 评分超阈 → `abnormal_trade_alert`，severity；`CRITICAL` 级触发限制开仓处置（联动 IntradayRisk），并可标记"上报"。
- 检测延迟目标 < 100ms（本地 Kafka 串行消费即可达标）。

**正常路径**：委托/成交流 → 窗口评分 → 命中 → 生成 alert + 处置建议。
**异常处理**：Kafka 消费断流 → 跳过该窗口，补对账说明；监控自身不阻塞交易。
**数据流向**：Kafka `topic.order`/`topic.trade` → ScoringEngine → PG `abnormal_trade_alert`；联动 alert_event 与 IntradayRisk。
**幂等**：同一窗口同一账户同一规则只生成一条活跃 alert（`OPEN` 状态去重）。

**API**：
- `GET /api/surveillance/abnormal/list?status=&accountId=` 异常告警列表（RISK/COMPLIANCE）
- `GET /api/surveillance/abnormal/{alertId}` 详情（RISK/COMPLIANCE）
- `POST /api/surveillance/abnormal/{alertId}/confirm` 确认并上报（RISK_OFFICER，确认口令）
- `POST /api/surveillance/abnormal/{alertId}/close` 关闭（RISK_OFFICER/COMPLIANCE）
- `GET /api/surveillance/abnormal/rules` 规则阈值查看（RISK/COMPLIANCE）

### 5.5 日终清算对账（DailySettlementService）

**功能描述**：交易日结束后批量清算——逐笔核对成交记录、计算当日盈亏/手续费/印花税；系统内部对账（交易 vs 资金）+ 外部对账（vs 柜台/交易所口径，本地用 Mock 柜台数据），产出差异工单。

**业务规则**：
- 批量流程状态机：`CREATED → RUNNING → REVIEWING → CLOSED/FAILED`；重复执行前需确认且仅对未 CLOSED 批次重跑。
- 清算内容：逐成交计算手续费/印花税、日盈亏（FIFO 持仓配对，与 P0 回测口径一致）、资金变动汇总。
- 对账维度：资金（可用/冻结/保证金）、持仓（数量/成本）、成交（笔数/金额）；差异大于容差（默认 0.01 CNY）生成 `settlement_diff_item` OPEN 工单。
- 差异处理：FUND_MANAGER 调账（记 `adjust_by`/`adjust_note`）→ `ADJUSTED` → 复核 CLOSED；差异清零后批次 CLOSED。
- 触发：手工 `POST /api/settlement/run` 或 `POST /api/settlement/run?tradeDate=yyyy-MM-dd`（重跑）。

**正常路径**：触发批次 → 读取当日成交/资金/持仓 → 逐项清算 → 生成报表（资金/持仓/成交/盈亏）→ 对账比对 → 差异工单 → 复核关闭。
**异常处理**：清算中途失败 → 批次 FAILED + 记录 error_message，可重跑（重新 CREATED）。与交易并行运行不锁表。
**数据流向**：`OrderMapper`/`FundFlowMapper` 读当日数据 → 清算引擎 → `settlement_batch` + `settlement_diff_item`；对账源为 `fund_reconciliation`（P0）。
**幂等**：`batch_id` 唯一键 + trade_date 唯一，同一交易日仅一个批次。

**API**：
- `POST /api/settlement/run` 运行清算（FUND_MANAGER/ADMIN）
- `GET /api/settlement/batches?tradeDate=` 批次列表（FUND_MANAGER/COMPLIANCE）
- `GET /api/settlement/batch/{batchId}` 批次详情（含汇总）（FUND_MANAGER/COMPLIANCE）
- `GET /api/settlement/batch/{batchId}/diffs` 差异工单（FUND_MANAGER/COMPLIANCE）
- `POST /api/settlement/diff/{diffId}/adjust` 调账（FUND_MANAGER）
- `POST /api/settlement/batch/{batchId}/close` 关闭批次（FUND_MANAGER，确认口令）
- `GET /api/settlement/report/{batchId}?type=fund|position|trade|pnl` 报表（FUND_MANAGER/COMPLIANCE）

### 5.6 组合管理（PortfolioService）

**功能描述**：组合定义（股票/期货/多资产）、约束（仓位上限/集中度/流动性）、再平衡触发（定期/阈值/事件）、调仓指令预览与执行。

**业务规则**：
- 组合定义：`portfolio` + `portfolio_position`；`config_json` 存约束/目标/再平衡参数。
- 再平衡触发：`SCHEDULED`（cron）、`THRESHOLD`（权重偏离 > deviation_pct）、`EVENT`（风险预算突破）、`MANUAL`。
- 再平衡流程：触发 → 预览指令（`preview_json`：买卖标的/数量/预计成本）→ 人工确认（默认）或 AUTO → 生成 OMS 订单 → 跟踪完成。
- 约束校验：指令生成前过组合约束（单标的/行业/杠杆上限），违反则剔除并说明原因。
- 执行模式：`MANUAL` 需确认口令。

**正常路径**：触发 → 计算目标权重 → 与当前持仓对比 → 生成指令预览 → 人工确认 → 拆单报单 → 完成回写 portfolio_position。
**异常处理**：资金不足的指令标记 `FAILED_INSUFFICIENT`，跳过该指令继续其余，批次记录原因。
**数据流向**：`portfolio`/`portfolio_position` → RebalanceEngine → `portfolio_rebalance_job` → OMS 下单。
**并发**：同一组合同一时间只允许一个 ACTIVE 再平衡任务（组合级互斥锁，Redis `SETNX portfolio:rebalance:{id}`）。

**API**：
- `POST /api/portfolio` 创建组合（TRADER）
- `GET /api/portfolio/list` 我的组合（TRADER/ADMIN 全量）
- `POST /api/portfolio/{portfolioId}/positions` 设置目标持仓（TRADER）
- `GET /api/portfolio/{portfolioId}` 组合详情+偏离度（TRADER/RISK/ADMIN）
- `POST /api/portfolio/{portfolioId}/rebalance` 触发再平衡（TRADER）
- `POST /api/portfolio/rebalance/{jobId}/preview` 预览指令（TRADER）
- `POST /api/portfolio/rebalance/{jobId}/confirm` 确认执行（TRADER，确认口令）

### 5.7 VaR 风险价值（VarCalculator）

**功能描述**：计算组合/账户/策略在置信度下的最大可能损失，支持历史模拟法（默认）与方差协方差法。

**业务规则**：
- 输入：持仓（portfolio_position / account 持仓快照）+ 历史行情（ClickHouse `lhjypt.bar` 日线）→ 计算组合日损益序列 → 取分位数。
- 方法：`HISTORICAL`（默认，1d 95%）、`VAR_COV`；`confidence`/`horizon_days` 可配。
- 触发：按需计算 `POST /api/var/compute`；记录 `var_calculation`（含 calc_time），查询最近值。
- 与风险预算联动：组合 `config_json.risk_budget.max_var_95_1d` 被比对，超限触发 IntradayRisk `VAR_LIMIT`。

**正常路径**：请求 → 拉持仓+历史行情 → 计算损益序列 → 分位数 → 返回 VaR + 记录。
**异常处理**：行情数据不足（< 60 个样本）→ 返回 `INSUFFICIENT_DATA` 错误，不产出虚假数值。
**数据流向**：持仓/历史行情 → VarCalculator → `var_calculation`；结果供 risk 消费。
**幂等**：无写副作用（仅记录）。

**API**：
- `POST /api/var/compute` 计算 VaR（体：scope_type/scope_id/method/confidence/horizon）（RISK/TRADER 自属）
- `GET /api/var/latest?scopeType=&scopeId=` 最近一次值（RISK/TRADER 自属）
- `GET /api/var/history?scopeType=&scopeId=&from=&to=` 历史序列（RISK/COMPLIANCE）

### 5.8 策略加密与沙箱（StrategySecurityService + StrategySandbox）

**功能描述**：策略源码加密存储（AES-256-GCM）、加载时校验（SHA-256 防篡改）、运行沙箱隔离（资源限制/API 白名单/超时/异常隔离）、全程操作审计。

**业务规则**：
- 加密：上传策略源码 → AES-256-GCM 加密 → 存 `strategy_crypto.ciphertext`；密钥按 `key_id` 引用（本地 `key-store.properties`，生产 HSM/KMS 预留 SPI）。
- 下载：解密出明文仅在进程内，日志与响应永不输出明文源码；下载需确认口令 + 审计。
- 校验：上传时记录 SHA-256，加载时重算比对，不一致拒绝运行。
- 沙箱执行：Python 策略由 `StrategySandbox` 以受限子进程运行（资源限制：CPU 时间 / 内存 / 网络禁用；注入式 API 白名单，禁文件/网络/系统调用；超时 30s 强杀）；单策略崩溃不影响 core 与其他策略。
- 审计：所有 UPLOAD/DOWNLOAD/RUN/STOP/EDIT_PARAM 记录 `strategy_audit_log`，保留 ≥3 年（P1 准出）。
- 容器沙箱（生产）预留：`SandboxExecutor` 接口，本地实现为子进程沙箱，生产实现为容器/Serverless，可配置切换。

**正常路径**：上传→加密+审计→加载→校验→解密到内存→沙箱启动→运行→资源回收；全程审计。
**异常处理**：解密失败/校验失败 → 拒绝启动 + 审计 ERROR；沙箱超时 → kill + 告警 + 策略状态 ERROR。
**数据流向**：`strategy_crypto`/`strategy_audit_log`/`strategy_info`。
**并发**：策略互不共享内存；同一策略不并发运行两个实例（Redis 锁）。

**API**：
- `POST /api/strategy/{strategyId}/code` 上传（加密存储）（TRADER：仅自属）
- `POST /api/strategy/{strategyId}/code/download` 下载（需确认口令并审计）（TRADER：仅自属）
- `POST /api/strategy/{strategyId}/run` 沙箱启动（TRADER：仅自属）
- `POST /api/strategy/{strategyId}/stop` 停止（TRADER：仅自属）
- `GET /api/strategy/audit?strategyId=&action=` 审计日志（COMPLIANCE/TRADER 自属）
- `GET /api/strategy/sandbox/status/{strategyId}` 沙箱运行状态（TRADER/ADMIN）

### 5.9 性能降级预案（DegradeController）

**功能描述**：过载保护、优雅降级、行情源降级、策略降级、熔断。核心交易链路优先保障。

**业务规则**：
- 过载保护：订单入口用 Redis 令牌桶限流（默认 500 TPS 本地、可配）；队列水位超阈值时拒绝非核心请求。
- 优雅降级：非核心功能（VaR 计算、报表、绩效归因）路由到低优先级线程池，过载时可临时关闭；`degrade_switch` 控制。
- 行情源降级：主行情源故障 → 自动切换备用（`MockMarketDataProvider` 即备选），降级为快照/低频率行情；`degrade_switch.degrade.market.backup`。
- 策略降级：按策略优先级暂停低优先级策略执行（保留交易中的收尾）。
- 熔断：对第三方/网关/大数据查询用 Resilience4j 熔断，熔断状态与恢复上报监控。
- 降级开关：`degrade_switch` 表支持热更新（每 10s 扫描生效），操作需确认口令并留痕，恢复自动或手动。

**正常路径**：负载正常 → 全功能；触发条件 → 自动降级 + alert_event 记录 → 恢复条件满足 → 自动回滚。
**异常处理**：降级开关误操作 → 可一键回滚（记录原状态）；核心链路（报单/撤单）永不降级。
**数据流向**：`degrade_switch` + Resilience4j 配置 → 各服务运行态。
**并发**：开关变更通过乐观锁（id 版本）+ Redis 广播提示刷新。

**API**：
- `GET /api/degrade/switch/list` 当前开关（ADMIN/RISK）
- `POST /api/degrade/switch` 设置开关（ADMIN，确认口令）
- `GET /api/degrade/status` 当前状态与熔断器（ADMIN）
- `POST /api/degrade/backup-market` 手动切换行情源（ADMIN）
- `GET /api/degrade/capacity` 容量水位（TPS/队列/资源）（ADMIN/RISK）

---

## 6. 安全设计方案

### 6.1 五层防护

**网络层**：本地开发不暴露公网；生产边界网关 + HTTPS/WSS（P2 对外）。监听端口仅绑定内网。
**应用层**：
- 鉴权：`AuthInterceptor` 读取 `X-User-Role`/`X-User-Id`（本地模拟）；生产由网关注入 JWT 声明（见 §3.3 兼容）。
- 权限：`@RequiresRole` AOP 校验 + 数据范围过滤；**所有 P1 写接口权限已在 §3.1 定义**。
- 注入防护：MyBatis-Plus 参数化查询（P0 已按此实践），新增 SQL 一律禁止字符串拼接；输入校验 `@Validated` DTO。
- 敏感操作二次确认：`X-Confirm-Token`（见 §3.5）。
**数据层**：
- 策略明文：只在内存解密，DB/日志不落明文（`strategy_crypto.ciphertext` 存密文）。
- 敏感文件：网关密码等只存 `key_id` 引用，不入库。
**运维层**：degrade 开关操作审计；`strategy_audit_log` 保留 ≥3 年；健康检查端点 `/actuator/health` 对管理员开放。
**密钥生命周期**：
- 密钥存 `config/key-store.properties`（本地，不入 Git，`.gitignore`），`key_id` 与 key 分离；生产换 KMS（预留 `CryptoKeyProvider` SPI）。

### 6.2 具体措施与实现方式

| 项 | 实现 |
|----|------|
| 策略加密 | AES-256-GCM，IV 随机 + key_id 关联；校验 SHA-256（checksum 字段） |
| 敏感数据（暂无 PII） | 网关密钥引用、策略密文；日志日志规范脱敏（§6.4） |
| API 文档 | 本地开 Swagger 预留；生产关闭（P2） |
| CORS | 默认同源；生产网关统一跨域 |
| 跨系统一致性 | 交易/清算沿用 OMS 幂等 + 对账差异工单（§5.5） |

### 6.3 API 错误码定义（P1 扩展）

| code | 含义 | 示例 |
|------|------|------|
| 0 | 成功 | - |
| 400 | 参数错误 | 非法日期/重复字段 |
| 401 | 未鉴权 | 缺失角色头 |
| 403 | 权限不足 | 数据范围越界 |
| 404 | 资源不存在 | gatewayId 不存在 |
| 409 | 冲突/需二次确认 | 无有效 X-Confirm-Token |
| 422 | 业务规则不满足 | 组合约束违反、资金不足 |
| 500 | 系统错误 | 解密失败 |
| 503 | 服务降级/不可用 | 无可用网关、清算中 |

### 6.4 日志设计规范

- 日志级别：`com.lhjypt` DEBUG（开发）/ INFO（生产）。
- 日志格式：`{ts} {level} [traceId] {logger} {message}`，`traceId` 由 `AuthInterceptor` 生成透传（请求头 `X-Trace-Id`）。
- 脱敏：日志中**禁止**输出策略明文、网关密钥、确认口令；金额可打码（`***`）。`LogSensitiveFilter` 统一拦截 `ciphertext`/`key`/`token` 字段。
- 审计日志（`strategy_audit_log`）与业务日志分离，审计不可删改。

### 6.5 敏感数据加密存储方案

- 策略源码：AES-256-GCM（见 §6.2）。
- 网关密钥：`config/key-store.properties` 引用（本地）；生产 KMS。
- 无用户 PII 存储（本平台为机构内部系统，无手机号/身份证）。

---

## 7. 开发规约与已知陷阱（P1 落地必读）

### 7.1 类型统一规约

- 时间字段：数据库统一 `TIMESTAMPTZ`，Java DTO 统一 `LocalDateTime`（UTC 存储），前端展示转 Asia/Shanghai。
- 主键：所有表统一 `BIGSERIAL`（Long），与 P0 `Order.id` 一致，不引入 UUID 混用。
- 金额：统一 `NUMERIC(20,4)`/`BigDecimal`，禁止 `double`。
- 枚举：存字符串（大写），如 `status` 值 `OPEN/CLOSED`，前后端一致；禁止大小写混用。

### 7.2 框架组合兼容性验证

- 开发第一个功能前先跑最小测试：Spring Boot 3.2.5 + MyBatis-Plus 3.5.5 + 新引入的 `spring-boot-starter-data-redis` + Resilience4j 组合的启动 Smoke Test（`SmokeTest` 类），确认 Redis 连接、Actuator 指标可用（对应 P0 已有 PG/CH/Kafka 组合验证经验）。
- Resilience4j 3.x 与 Boot 3 兼容：用 `resilience4j-spring-boot3`，配置在 `application.yaml`，别用旧 2.x 包。
- Lombok 1.18.32 + Java17 已知兼容（P0 已验证）。

### 7.3 外键依赖与数据操作顺序

- 表依赖拓扑（新增部分）：`strategy_info` → `strategy_crypto`/`strategy_audit_log`；`portfolio` → `portfolio_position`/`portfolio_rebalance_job`；`settlement_batch` → `settlement_diff_item`。
- 种子/测试清理按依赖逆序（先 diff 再 batch，先 position 再 portfolio）。
- 级联策略：strategy_crypto 随 strategy 逻辑删除（软删）；audit_log 保留不随删（RESTRICT）；settlement 相关禁止级联删。

### 7.4 全局副作用清单与测试隔离

- Redis：限流桶 key、组合互斥锁 key、处置去重 key；测试用独立 db（`redis.database=1`）。
- 定时器：清算/监控告警/降级扫描定时任务在测试禁用（`@TestPropertySource(lhjypt.scheduler.enabled=false)`）。
- ClickHouse `metric_sample`：测试写入后清理（按测试标识）。
- 全局单例：GatewayRouter 连接池、策略沙箱进程池，测试用 Mock 实例。

### 7.5 本地与生产差异对照

| 维度 | 本地（Docker compose） | 生产（预留） |
|------|----------------------|-------------|
| 网关 | Mock 网关 | CTP 真实/适配器 + 行情源 |
| 密钥 | key-store.properties | KMS/HSM |
| 监控 | Actuator 纯本地 | Prometheus + Alertmanager |
| 沙箱 | 子进程 | 容器/Serverless |
| 鉴权 | 头注入模拟 | 网关注入 JWT |
| 通知 | log/webhook | 钉钉/短信/邮件 |

### 7.6 数据库连接与 ORM

- 连接池：HikariCP（Spring Boot 默认），pool_size 20 / max 30 / `pool_pre_ping=true`。
- 事务边界：清算批次整体事务；策略上传（加密+审计）同一事务；路由规则更新同事务。
- 慢查询：对 `settlement`/`abnormal` 窗口扫描加索引；MyBatis 日志开启（P0 已开 stdout）。

---

## 8. 测试策略

### 8.1 测试基础设施验证（开发第一天）

- 写一个最小异步/同步集成测试：`ApplicationSmokeTest` 验证 Spring 上下文 + PG + Redis + Actuator 指标可启动（参照 P0 已通过的 14 用例模式）。
- 测试数据库：独立 PG database（`lhjypt_test`）与 Redis db=1，绝不复用开发库。

### 8.2 核心 API 测试用例（至少覆盖）

| 模块 | 用例 |
|------|------|
| 网关 | 注册→连接→路由命中→主故障切 fallback→熔断后路由 |
| 监控告警 | 指标采样→规则命中→告警生成→ack/close |
| 事中风控 | 持仓超限→BLOCK→处置记录；强平确认流 |
| 异常交易 | 自成交事件→告警生成→确认上报 |
| 清算 | 造数→批次→对账差异→调账→关闭；重跑幂等 |
| 组合 | 建组合→再平衡预览→确认→订单生成→约束拦截 |
| VaR | 造行情→计算→置信度分位数正确→数据不足报错 |
| 策略加密 | 上传→密文落库（明文不可查）→下载需口令→解密失败拒绝 |
| 降级 | 开关切换→熔断触发→自动恢复 |

### 8.3 关键测试数据管理

- 用 MyBatis-Plus + 手写 `TestDataFactory` 造数（账户/持仓/成交流）。
- 测试清理：按 §7.3 逆序 DELETE + Redis FLUSHDB（db=1）。

### 8.4 压力测试（本地）

- 场景：监控采样 1000 账户、异常交易窗口 10 万事件、清算 100 账户。
- 工具：`wrk`/JMeter 接口压测。
- 通过：各接口 P99 < 50ms（本地 Mock 数据）、风控校验 < 1ms、告警检测 < 100ms。

---

## 9. 并发承载分析

### 9.1 峰值流量估算（P1 机构内网工具）

- 日活：机构内部约 50~200 用户（交易员/风控/清算/合规），无集中秒杀场景。
- 峰值 QPS ≈ 日活 × 每用户请求/86400 × 峰值系数：200 × 200 / 86400 × 10 ≈ **46 QPS**（接口侧余量充足）。
- 关键：**订单 TPS 不由用户数决定，由策略自动交易频率决定**。P1 目标 200 策略 × 每策略平均 2 单/秒 = 400 TPS 订单，峰值 5000 TPS 需网关+批量聚合支撑（本地按 500 TPS 压测验证管线）。

### 9.2 并发承载分析表

| 操作 | 同时量 | 处理 | 响应/时延 | 结论 |
|------|--------|------|----------|------|
| 报单（经网关） | 500 TPS | 串行网关分发+OMS 幂等 | P99<50ms | OK（Mock 验证） |
| 成交流事件 | 500 TPS | Kafka 异步消费 | 检测<100ms | OK |
| 异常交易评分 | 500 事件/s | 流式窗口 | <100ms | OK |
| 监控采样 | 1000 账户/10s | 批量采样 | 秒级吞吐 | OK |
| VaR 计算 | 并发 5 | 每账户 <1s | <1s | 需线程池限并发 |
| 日终清算 | 100 账户 | 批量作业 | <1min | OK |

### 9.3 持久化与一致性设计

- 事件 → PG/CH 落库 + Kafka 发布（同一事务内先落库后发 Kafka；consumer 幂等消费：按事件 ID 去重，落 `event_dedup` 简易表或用业务唯一键）。
- 跨系统一致性（交易 vs 资金 vs 对账）：依赖 P0 OMS 幂等（orderId） + 清算对账差分（§5.5），无需分布式事务框架。
- Redis 使用点位已列于 §4.7/§7.4；限流桶 + 组合锁 + 处置去重均带 TTL 自动过期。

---

## 10. 架构韧性设计

### 10.1 单点故障与消除

| 组件 | 是否单点 | P1 缓解 |
|------|---------|---------|
| PostgreSQL/ClickHouse/Kafka/Redis | 单实例（compose） | 数据落库即持久；P2 异地灾备 |
| GatewayRouter | 进程内单点 | 路由规则落 PG，重启可重建 |
| 策略沙箱进程池 | 单机 | 崩溃自动重启 + 审计 |
| 定时器（清算/告警/降级） | 单机 @Scheduled | 幂等防重；P2 分布式调度 |

### 10.2 故障影响矩阵

| 故障 | 受影响 | 不受影响 |
|------|--------|----------|
| PG 故障 | 业务查询/下单落库失败 | 内存快照、日志告警 |
| Kafka 故障 | 事件流消费停止（可恢复重放） | 直连查询、Mock 行情本机 |
| Redis 故障 | 限流降级失效（退化为不限制） | 交易主链路、清算 |
| CH 故障 | 指标/行情历史不可查 | 实时交易（行情走 Mock/内存） |
| 网关全部 DOWN | 报单失败（OMS 拒单） | 回测/监控/清算/审计 |
| 策略沙箱崩溃 | 该策略停止 | 其他策略、交易引擎 |

### 10.3 RTO/RPO 定义

- **RTO**：核心交易链路 30 分钟内恢复（本地单实例重启 < 5min）。
- **RPO**：0（关键写入强一致落库后响应；Kafka 事件在落库后发，未消费可重放）。

### 10.4 熔断器设计

| 调用 | 熔断参数 | 降级行为 |
|------|---------|----------|
| CTP/网关 | 50% 错误/5s → 熔断 30s | 路由 fallback / `GATEWAY_UNAVAILABLE` |
| ClickHouse 查询（VaR/报表） | 60% 超时/10s → 熔断 30s | 返回 `DEGRADED`，仅明细降级 |
| 外部通知（webhook/钉钉） | 失败 3 次/5s → 熔断 2min | 仅 log，告警仍落库 |
| 行情源切换 | 主源断流 10s | 切备用行情源（MockMarketDataProvider） |

### 10.5 自动恢复机制

- 进程：Spring Boot 自动重启（compose `restart: unless-stopped`）；连接池 `pool_pre_ping`。
- Kafka/Redis/CH：客户端自动重连（Spring 默认）。
- 策略沙箱：崩后自动重启（需审核通过的策略才允许自恢复）。
- 熔断状态：半开状态自动探测恢复。

### 10.6 优雅降级层级

| 级别 | 状态 | 动作 |
|------|------|------|
| L0 | 正常 | 全功能 |
| L1 | 部分降级 | 关闭 VaR/报表（degrade.switch），行情降频 |
| L2 | 核心降级 | 暂停低优先级策略，限制开仓 |
| L3 | 紧急 | 仅撤单/风控放行，冻结新报单 |
| L4 | 完全不可用 | 通知 + 人工应急预案 |

### 10.7 超时分级策略

| 操作 | 超时 | 用户看到 |
|------|------|----------|
| REST 读写 | 2s | 503/重试 |
| 报单（网关） | 2s | `GATEWAY_TIMEOUT` + 状态保持 PENDING |
| VaR 计算 | 5s | `INSUFFICIENT_DATA`/降级 |
| 清算批次 | 30min | 进度回调查询 |
| 策略沙箱运行 | 30s 超时强杀 | 策略 ERROR |

### 10.8 兜底清单

| 外部依赖 | 兜底 |
|----------|------|
| 真实行情 | Mock 行情源（P0 已有） |
| 通知渠道 | log 兜底，事件落库 |
| KMS/HSM | 本地 key-store（生产替换） |
| 分布式调度 | 本机 @Scheduled + 幂等 |

---

## 11. 开发里程碑（三批实施）

> P1 deadline 目标 2026-12-31。三批内部按"监控→风控→降级"实施（已确认）。

| 批次 | 阶段 | 内容 | 交付物 |
|------|------|------|--------|
| 第一批 | 1 | 监控告警（MetricsSampler/AlertService/告警规则与事件） | 7 接口 + metric_sample 表 |
| | 2 | 事中风控（IntradayRiskService + 处置链） | 6 接口 + 规则/处置表 |
| | 3 | 异常交易监控（AbnormalTradeMonitor） | 5 接口 + alert 表 |
| | 4 | 性能降级（DegradeController + Resilience4j） | 5 接口 + degrade_switch |
| 第二批 | 5 | 多柜台多通道（GatewayRouter） | 8 接口 + 网关/路由表 |
| | 6 | 日终清算对账（DailySettlementService） | 7 接口 + 批次/差异表 |
| 第三批 | 7 | 组合管理（组合/约束/再平衡） | 7 接口 + 组合/持仓/作业表 |
| | 8 | VaR 计算 | 3 接口 + var 表 |
| | 9 | 策略加密与沙箱 | 6 接口 + crypto/audit 表 |
| | 10 | 全量回归 + 一致性巡检 + 文档更新 | 测试报告/巡检报告 |

**推荐开发模式**：混合模式——批量任务由 agent-continuous-workflow 串行推进（与 P0 一致），每批内置测试与自检；核心风险点（网关路由、强平处置、策略沙箱）人工评审。

---

## 12. 目录结构（P1 增量）

```
lhjypt/
├─ docs/
│  ├─ p1-prd.md                      # 本文档
│  └─ ...
├─ core/src/main/java/com/lhjypt/core/
│  ├─ common/                        # Result + 日志脱敏 + 权限注解
│  ├─ controller/
│  │  ├─ GatewayController.java      # 多柜台
│  │  ├─ MonitorController.java      # 监控告警
│  │  ├─ IntradayRiskController.java # 事中风控
│  │  ├─ SurveillanceController.java # 异常交易
│  │  ├─ SettlementController.java   # 日终清算
│  │  ├─ PortfolioController.java    # 组合管理
│  │  ├─ VarController.java          # VaR
│  │  ├─ StrategySecurityController.java # 策略加密沙箱
│  │  └─ DegradeController.java      # 性能降级
│  ├─ service/
│  │  ├─ gateway/                    # GatewayRouter + 网关实现
│  │  ├─ monitor/                    # Metrics + Alert
│  │  ├─ risk/intraday/              # 事中风控
│  │  ├─ surveillance/               # 异常交易
│  │  ├─ settlement/                 # 日终清算
│  │  ├─ portfolio/                  # 组合管理
│  │  ├─ var/                        # VaR
│  │  ├─ strategy/                   # 加密/沙箱/审计
│  │  └─ degrade/                    # 降级开关
│  ├─ model/                         # P1 实体（对应 §4 表）
│  ├─ mapper/                        # P1 Mapper
│  └─ config/
│     ├─ GatewayConfig.java
│     ├─ RiskIntradayConfig.java
│     └─ KeyStoreProperties.java
├─ scripts/
│  └─ init-p1.sql                    # P1 新增表（§4.2）
└─ .workflow/
   ├─ tasks-p1.md                    # P1 任务拆解（本规划同步）
   └─ context.yaml（更新 P1 状态）
```

---

## 13. 成本估算与运维

### 13.1 成本估算

| 项 | P1（本地） | 生产（预留，估算） |
|----|-----------|-------------------|
| 服务器 | 本机 Docker（0 增量） | 4 核 8GB×2 ~ ¥500-1000/月 |
| 中间件 | compose 现有（0） | 云 PG/CH/Kafka 托管 ~ ¥2000-5000/月 |
| 加密 | JDK 内置（0） | KMS ¥几十/月 |
| 监控 | Actuator 本地（0） | Prometheus 自托管（≈0 增量） |
| SDK 策略运行 | 本机进程（0） | 容器调度 ~ ¥1000-3000/月 |
| **合计** | 0（本地可运行） | ~¥5k-1w/月（生产化） |

### 13.2 运维手册要点（P1）

- 日常：`docker compose ps` 健康检查 → 5 中间件；`/actuator/health` 200。
- 告警事件：`GET /api/monitor/alerts?status=OPEN` 排查；关键告警接 webhook/钉钉。
- 备份：PG `pg_dump` 每日（策略密文、规则、订单在 PG）；关键配置 `key-store.properties` 单独备份并异地。
- 更新回滚：停服→替换 jar→启动；降级开关可先限流再发布。
- 日志排查：`logging.level.com.lhjypt=INFO`；traceId 关联 Kafka 事件排查。
- 性能基线：P0/P1 关键 API 记录 P50/P95/P99，存 `docs/performance-baseline.md`（首次压测后生成）。

---

## 14. 附录

- 本文档与 P0 的差异：无独立前端（管理用 API+静态看板）、无 OAuth 服务端、无分布式调度；均列入 P2 规划。
- **V2（P2 三期）规划**：多租户隔离、完整监管报送与 AML、微秒级链路、异地灾备、知识产权水印、自动伸缩（对齐 requirements-supplement.md）。
- 本 PRD 状态：Final（经需求确认与走查）。

---

## 变更记录

| 版本 | 日期 | 说明 |
|------|------|------|
| v1.0 | 2026-10-02 | 创建：P1 二期完整开发规范，9 功能模块全量规划，三批实施（监控→风控→降级） |