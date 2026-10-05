# P1 二期发布前一致性巡检报告

> 任务：TASK-20261002-001 企业级量化交易平台（lhjypt）P1 二期
> 日期：2026-10-05
> 巡检范围：P1 九项任务（监控告警/事中风控/异常交易/性能降级/多柜台/日终清算/组合管理/VaR/策略加密沙箱）
> 结论：**四维一致，PASS**

## 1. 代码一致性 — PASS

| 检查项 | 结果 |
|--------|------|
| 构建与测试 | `mvn verify`：158/158 通过，0 跳过；JaCoCo 门禁通过 |
| 控制器/接口 | 18 个控制器、117 个映射端点；P1 九模块接口齐全且与 PRD §12 目录一致 |
| 分支规约 | `feature/p1-00x-*` 命名统一；基线提交可追溯 |
| 依赖版本 | 延续 P0（Spring Boot 3.2.5 / MyBatis-Plus 3.5.5 / ClickHouse JDBC 0.6.0），无新增中间件 |
| 敏感信息 | 密钥入 `config/key-store.properties`（gitignore）；数据库口令走环境变量；CTPConfig 已脱敏 |
| 死代码/告警 | 无未使用主代码；Lombok 注解处理已在 pom 显式配置 |

## 2. 环境一致性 — PASS

| 检查项 | 结果 |
|--------|------|
| 中间件 | 5 容器健康：PG15 / ClickHouse 23.8 / Kafka 3.7 / Redis 7 / ZK |
| 运行时 | JDK 17.0.20 + Maven 3.10，服务 8081 正常启动 |
| 健康检查 | `/actuator/health` UP（PG + CH 双数据源） |
| 跨模块冒烟 | 12 个模块代表接口全部 `code=0`：gateway/monitor×2/risk/surveillance/settlement/portfolio/var/strategy/degrade/trade×2 |
| 配置一致 | `application.yaml` 的 PG/CH/Kafka/Redis 与 compose 对齐；新增 strategy.crypto/sandbox 默认值与环境变量占位 |

## 3. 数据一致性 — PASS

| 检查项 | 结果 |
|--------|------|
| 表结构 | PG 34 张表；P1 新增表（网关/路由/策略/组合/事中/异常/清算/告警/VaR/降级）齐全 |
| 数据字典 | 34 表结构与 `init-p1.sql`/`init-db.sql`(+migration) 对齐，Testcontainers schema 测试通过 |
| 时序数据 | ClickHouse `lhjypt`：tick_data/bar_data/metric_sample/market_subscription |
| 业务数据 | orders=9、trade_record=5、position=4、fund_account=1、settlement_batch=1(CLOSED)、var_calculation=8 |
| 对账 | 清算批次 `STL-20261005-C9E47C` 差异工单已闭环，状态 CLOSED |

## 4. 文档一致性 — PASS

| 检查项 | 结果 |
|--------|------|
| 接口文档 | PRD §5.1–§5.9 接口清单与控制器实现逐一比对，无缺失/多余 |
| 设计文档 | §4 表设计以 `init-p1.sql` 为准；§5.7 VaR 行情表名已修正为 `bar_data` |
| 变更记录 | `.workflow/tasks-p1.md` 状态、`context.yaml` 上下文、各任务计划/测试报告齐全 |
| 发布说明 | README 项目状态、交付报告已更新 |

## 5. 遗留与风险

| 项 | 说明 | 处置 |
|----|------|------|
| 本地开发密钥兜底 | 未配置 `key-store.properties` 时使用派生密钥 | 生产切换 KMS/HSM（SPI 已预留） |
| 沙箱为进程级 | 网络/系统调用以审计钩子 best-effort 拦截 | 生产切容器沙箱（`SandboxExecutor` 可替换） |
| 多实例定时器 | `@Scheduled` 单机幂等 | P2 分布式调度 |

上述均为 PRD 明确的生产化预留项，不阻断 P1 交付。
