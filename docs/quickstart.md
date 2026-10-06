# lhjypt 单人快速上手（Quickstart）

> 目标：**一条命令跑起全平台**，从启动到第一次策略运行不超过 5 分钟。
> 适用：单人使用（仅你本人），本机 macOS/Linux，Docker + JDK17 + Maven + Node 已安装。

## 1. 一键启动

```bash
./scripts/dev.sh up
```

该命令幂等、可重复执行，依次完成：
1. 启动中间件 5 容器（PostgreSQL / ClickHouse / Kafka / Zookeeper / Redis）并等待健康
2. 应用增量初始化 SQL（init-p1 / init-p2 / 全部 p2 迁移，均为 `IF NOT EXISTS` 幂等）
3. 构建（仅首次或 `LHJYPT_REBUILD=1`）并启动后端 8081
4. 构建（仅首次）并启动前端 5173（代理到 8081）

## 2. 常用命令

| 命令 | 说明 |
|------|------|
| `./scripts/dev.sh status` | 中间件/后端/前端健康一览 |
| `./scripts/dev.sh down` | 停止前后端与中间件（保留数据库卷） |
| `./scripts/dev.sh reset-db` | 删除数据卷并重建初始化（数据归零） |
| `./scripts/dev.sh logs backend` | 跟踪后端日志 |
| `./scripts/demo.sh` | 一键演示：上传策略→水印→沙箱运行→停止→审计 |
| `mvn verify`（Docker 可用时） | 全量构建 + 252 测试 + Testcontainers schema + JaCoCo |

> 本机当前使用 Lima Docker（`~/.lima/tuna-docker`）；其他 Docker 环境设置
> `LHJYPT_DOCKER_HOST=unix:///path/docker.sock` 即可。

## 3. 一次策略开发闭环（最小路径）

```bash
# 1) 启动平台
./scripts/dev.sh up

# 2) 跑演示验证链路
./scripts/demo.sh

# 3) 写策略（Python SDK）
#    SDK 位于 python/strategy_sdk，示例策略见 python/strategy_sdk/examples
#    BacktestAPI：回测；LiveOrderAPI：实盘下单（API 均指向前端 5173 代理）

# 4) 用前端管理
#    打开 http://localhost:5173 —— 策略、交易、风控、监控、数据等页面
```

## 4. 角色与请求头（接口调试用）

接口以 `X-User-Id` / `X-User-Role` / `X-Tenant-Id` 头模拟身份：

| 角色 | X-User-Role | 可访问 |
|------|-------------|--------|
| 管理员 | ADMIN | 全部，含租户/容量/汇率/监管配置 |
| 交易员 | TRADER | 策略、交易、组合 |
| 风控 | RISK_OFFICER | 风险预算、盘中风控 |
| 合规 | COMPLIANCE | 监管报送、AML、归因 |
| 资金 | FUND_MANAGER | 资金、清算、结算 |

示例：

```bash
curl -X POST http://localhost:8081/api/strategy/S-1/code \
  -H 'X-User-Role: TRADER' -H 'X-User-Id: trader-1' -H 'Content-Type: application/json' \
  -d '{"sourceCode":"def on_bar(ctx):\n    pass\n"}'
```

## 5. 关键约束（单人场景备注）

- **多租户**：默认 `DEFAULT` 租户，单人可完全忽略；`X-Tenant-Id` 不传即可。
- **环境绑定**：策略上传绑定本机（hostname+MAC）指纹；换机器运行需重新上传。
- **实盘**：默认 `MOCK` 网关（本地模拟成交）；接入真实柜台时补齐柜台适配层（见 `docs/architecture.md`）。
- **数据**：行情默认 Mock 源；接入真实数据源后策略与回测面向真实数据。

## 6. 排障

| 现象 | 处置 |
|------|------|
| `dev.sh up` 中间件等待超时 | 检查 Docker 连接与镜像，`docker ps` 看 5 容器状态 |
| 后端 8081 起不来 | `./scripts/dev.sh logs backend` 查看；多为端口占用或 DB 未就绪 |
| 前端 5173 白屏 | `./scripts/dev.sh logs frontend`；`npm install` 后重试 |
| 数据库重置 | `./scripts/dev.sh reset-db`（清空全部数据） |

详细架构与技术栈见 `docs/architecture.md`、`docs/tech-stack.md`；开发基线见 `.workflow/` 下各交付报告。