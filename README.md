# lhjypt - 企业级量化交易平台

> 覆盖行情、回测、交易、风控、资金、合规的全链路量化交易平台

## 项目状态

| 阶段 | 状态 | 截止时间 |
|------|------|----------|
| P0 一期（基础闭环） | 已完成 | 2026-11-15 |
| P1 二期（机构化） | 已完成 | 2026-12-31 |
| P1.5 交易主链路闭环与工程化加固 | 已完成 | - |
| P2 三期（合规监管·多租户·自动伸缩） | 进行中 | - |

## 技术栈

- **核心服务**：Java 17 + Spring Boot 3.2
- **策略SDK**：Python 3.9+
- **数据存储**：PostgreSQL 15 + ClickHouse 23
- **消息队列**：Kafka 3.7
- **缓存**：Redis 7

## 快速开始

### 1. 启动依赖环境
```bash
cp .env.example .env
docker compose -f config/docker-compose.yaml up -d
```

### 2. 启动核心服务
```bash
mvn clean package
java -jar core/target/core-0.1.0-SNAPSHOT.jar
```

### 3. 安装策略SDK
```bash
cd strategy-sdk
pip install -e ".[dev]"
pytest tests/ --cov=lhjypt --cov-report=term --cov-fail-under=80
```

## 项目结构

```
lhjypt/
├── core/                    # Java核心服务（行情/交易/风控/资金）
│   └── src/main/java/com/lhjypt/core/
├── strategy-sdk/            # Python策略SDK
│   └── lhjypt/
├── config/                  # 配置文件（docker-compose等）
├── scripts/                 # 脚本（数据库初始化等）
├── docs/                    # 文档（架构、技术选型）
└── .workflow/               # 工作流管理（任务跟踪、上下文）
```

## 模块说明

| 模块 | 说明 |
|------|------|
| market-data | 行情接入、清洗、存储、分发 |
| order-management | OMS订单全生命周期管理 |
| execution | CTP柜台对接、订单路由 |
| risk-control | 事前/事中风控、黑白名单 |
| fund-management | 资金冻结/解冻、账户管理 |
| backtest-engine | tick级回测引擎 |
| strategy-sdk | Python策略开发工具包 |

## 开发规范

- 代码提交前通过 `mvn verify` 和 `pytest`
- 接口返回统一使用 `Result<T>` 包装
- 数据库操作使用 MyBatis-Plus
- 策略开发继承 `lhjypt.Strategy` 基类
- 生产凭据只能通过环境变量注入，禁止提交到仓库

## 文档

- [技术选型](docs/tech-stack.md)
- [架构设计](docs/architecture.md)
- [P1.5 交易主链路闭环需求](docs/p1.5-trade-closure-requirements.md)
- [需求规格（补充）](.workflow/requirements-supplement.md)
- [P0任务拆解](.workflow/tasks-p0.md)
