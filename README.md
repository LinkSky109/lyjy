# lhjypt · 量化交易平台

覆盖行情、回测、模拟/实盘交易、风控、资金清算与合规的单机量化平台。

## 项目状态

| 阶段 | 内容 | 状态 |
|---|---|---|
| P0 | 行情 / OMS / 风控 / 资金 / 回测基础闭环 | 已完成 |
| P1 / P1.5 | 机构化、交易主链路闭环与工程化加固 | 已完成 |
| P2 | 合规监管、多租户、容量与归因 | 已完成 |
| P3 | 单机生产化：常驻、备份、可观测、密钥、行情可靠 | 已完成 |
| P4 | 行情分页回补、巡检时区、Prometheus/Grafana/Alertmanager | 已完成 |
| P5 | 前端改版（设计令牌、深浅主题、15 页统一交互） | 已完成 |
| P5 API | 仪表盘聚合、合约/行情、委托分页、风控规则、资金分页、组合净值/归因 | 已完成 |
| v1.1 | 交易工作台、决策首页、风控阈值在线编辑、账户互转、再平衡向导 | 已完成 |

> 单人使用场景：`GLOBAL_DONE`。当前重心：开箱即用、日常可用与前端一致性。

## 快速开始

```bash
cd /Users/link/myApp/lhjypt
./scripts/start.sh         # 一键：中间件 + 增量SQL + 后端 8081 + 前端 5180
```

- 前端：**http://localhost:5180**（本机 `.env` 配置 `LHJYPT_WEB_PORT=5180`，避开 5173 的其它服务）
- 后端：**http://localhost:8081**（健康检查 `/actuator/health`）
- 数据库：PostgreSQL **5433**（5432 被其它项目占用）
- 重建后端：`LHJYPT_REBUILD=1 ./scripts/start.sh`
- 登录：本地模拟身份，默认 `ADMIN / admin`
- 观测栈（可选）：`./scripts/obs.sh up` → Prometheus `9090`、Grafana `3000`

```bash
./scripts/demo.sh           # 上传策略→水印→沙箱运行→停止→审计
./scripts/channel-demo.sh   # Mock 通道：SDK 下单→成交→持仓/资金闭环
./scripts/dev.sh status|down|reset-db|logs backend
```

> `.env` 不入库；本机 PostgreSQL 发布在 **5433**（5432 被其它项目占用），`dev.sh` 会自动加载 `.env`。

## 技术栈

- 核心：Java 17 + Spring Boot 3.2.5 + MyBatis-Plus
- 策略：Python 3.9+ SDK（回测 / 实盘 / 指标 / 沙箱）
- 存储：PostgreSQL 15（交易/资金）+ ClickHouse 23（行情）
- 中间件：Kafka 3.7、Redis 7、Zookeeper
- 前端：React 18 + TypeScript + Vite（15 条路由，CSS 变量主题）
- 运维：Docker Compose、Prometheus + Grafana + Alertmanager

## 目录

```
core/           Java 核心服务（controller/service/mapper/model）
strategy-sdk/   Python 策略 SDK 与示例
web/            React 管理端
config/         docker-compose 与观测栈
scripts/        启动、演示、巡检、迁移脚本
docs/           架构、快速上手、部署、观测、行情、安全
.workflow/      分阶段计划、交付报告、一致性校验
design/         前端规范与静态原型（只读参考，不入库）
```

## 主要能力

| 域 | 能力 |
|---|---|
| 行情 | Gate.io / Binance Vision、真实 K 线、分页回补、质量巡检、多源容灾 |
| 交易 | OMS 全生命周期、Mock/CTP 网关、合约搜索、下单预检、委托/成交分页、批量撤单、键盘快捷键 |
| 风控 | 事前规则（持仓/资金/频率/自成交/黑名单）、运行时开关、阈值在线编辑、风险预算预警、VaR、事中限制 |
| 资金 | 出入金申请/审核、待审批队列、账户互转与双边流水、账户与流水分页、服务端余额二次校验、对账 |
| 组合 | 组合定义、再平衡、净值快照、策略贡献归因 |
| 治理 | 多租户、监管报送、异常交易、策略加密/水印/沙箱、降级开关、审计 |
| 前端 | 决策首页（待办/今日盈亏/动态）、交易工作台、审批时间线、深浅主题、移动端抽屉与表格堆叠 |

## 验证

```bash
mvn -pl core verify                              # 345 tests / 0 fail / 1 skip + JaCoCo
cd strategy-sdk && python -m pytest tests -q     # 29 passed
cd web && npm run build                          # tsc --noEmit + vite build
```

## 文档

- [快速上手](docs/quickstart.md)
- [架构设计](docs/architecture.md)
- [技术选型](docs/tech-stack.md)
- [部署与常驻](docs/deploy.md)
- [观测栈](docs/observability.md)
- [交易通道](docs/channel.md)
- [真实行情](docs/market-real.md) · [行情可靠性](docs/market-reliability.md)
- [指标口径](docs/metrics.md) · [安全](docs/security.md) · [备份](docs/backup.md)
- [v1.1 功能优化设计](docs/optimization-design.md)
- [前端改版规范](design/DESIGN-SPEC.md) · [后端接口规划](.workflow/frontend-api-plan.md)

## 开发约定

- 提交前：`mvn -pl core verify`、`python -m pytest tests`、`npm run build`
- 接口统一 `Result<T>`；数据库使用 MyBatis-Plus；策略继承 `lhjypt.Strategy`
- 生产凭据仅通过环境变量 / 密钥服务注入，禁止入库
- 单人场景默认 `DEFAULT` 租户、`MOCK` 交易网关
