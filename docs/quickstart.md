# lhjypt 单人快速上手

> 目标：一条命令跑起全平台，从启动到第一次策略/交易闭环不超过 5 分钟。
> 适用：本机 macOS/Linux，已安装 Docker + JDK17 + Maven + Node。

## 1. 一键启动

```bash
cd /Users/link/myApp/lhjypt
./scripts/dev.sh up
```

依次完成：
1. 启动中间件（PostgreSQL / ClickHouse / Kafka / Zookeeper / Redis）并等待健康
2. 应用 `init-p1.sql`、`init-p2.sql` 与 `scripts/migration/*.sql`（幂等）
3. 构建（首次或 `LHJYPT_REBUILD=1`）并启动后端 `8081`
4. 构建（首次）并启动前端（端口取 `LHJYPT_WEB_PORT`，默认 5173）

本机 `.env` 已配置：
- `LHJYPT_WEB_PORT=5180`（5173 被其它服务占用）
- `POSTGRES_URL=jdbc:postgresql://127.0.0.1:5433/lhjypt`（5432 被其它项目占用）

访问：
- 前端：http://localhost:5180
- 后端：http://localhost:8081
- 登录：默认 `ADMIN / admin`（本地模拟身份）

## 2. 常用命令

| 命令 | 说明 |
|---|---|
| `./scripts/dev.sh status` | 中间件/后端/前端状态 |
| `./scripts/dev.sh down` | 停止前后端与中间件（保留数据卷） |
| `./scripts/dev.sh reset-db` | 删除数据卷并重建（数据归零） |
| `./scripts/dev.sh logs backend` | 跟踪后端日志 |
| `./scripts/demo.sh` | 策略上传→水印→沙箱运行→停止→审计 |
| `./scripts/channel-demo.sh` | Mock 通道下单→成交→持仓/资金闭环 |
| `./scripts/obs.sh up` | Prometheus(9090) + Grafana(3000) |
| `./scripts/healthcheck.sh` | 中间件/后端/数据质量巡检 |

## 3. 模拟盘

1. 登录 http://localhost:5180，角色选 `TRADER`。
2. 连接 Mock 柜台：

```bash
./scripts/channel-demo.sh
```

3. 「交易下单」填标的/价格/数量 → 提交 → 二次确认。
   - 默认 Mock 即时成交
   - 验证待成交/撤单：

```bash
LHJYPT_TRADE_MOCK_FILL=false ./scripts/dev.sh restart
# 下单停在 SUBMITTED，可在「委托」页撤单；验证后恢复
./scripts/dev.sh restart
```

## 3.5 v1.1 功能入口

- **交易工作台** `/trading`：合约输入支持搜索/下拉（`rb2410` 等已配置合约）；下单前展示名义价值、预计保证金与下单后可用；委托页支持批量撤单；成交页支持分页与合约筛选；快捷键 `/`、`Alt+B`、`Alt+S`、`Ctrl/Cmd+Enter`。
- **决策首页** `/dashboard`：待办中心（待审出入金、未确认/严重告警、可撤委托）、今日盈亏与手续费、最近动态时间线。
- **风控** `/risk`：事前规则阈值在线编辑（`POSITION_LIMIT` / `FUND_CHECK` / `FREQ_LIMIT`），保存后立即生效；风险预算占用率进度条。
- **资金** `/fund`：新增「账户互转」页签与待审批队列；划转写双边流水，支持幂等键。账户互转至少需要两个资金账户。
- **组合** `/portfolio`：目标持仓支持向导模式；再平衡按「触发/加载 → 预览 → 确认执行」三步推进。

新增表 `fund_transfer` 由 `scripts/migration/p6_001_fund_transfer.sql` 创建；`./scripts/dev.sh up` 会自动应用 `scripts/migration/*.sql`。

## 4. 策略开发与回测

```bash
cd strategy-sdk
pip install -e ".[dev]"

# 示例：双均线真实行情回测
PYTHONPATH=. python lhjypt/examples/real_backtest_demo.py BTC_USDT 1h 7

# 示例：RSI 均值回归
PYTHONPATH=. python lhjypt/examples/rsi_backtest.py BTC_USDT 1h 7
```

策略继承 `lhjypt.Strategy`，实现 `on_init` / `on_bar`；示例见
`strategy-sdk/lhjypt/examples/`。

前端「回测」页支持：
- 基线回测
- 参数网格优化（`/api/backtest/optimize`）
- 组合资金分配（`/api/backtest/portfolio-optimize`）
- 稳健性检验（`/api/backtest/optimize/robustness`）
- 应用最优参数（`/api/backtest/optimize/apply`）

## 5. 角色与请求头

接口用 `X-User-Id` / `X-User-Role` / `X-Tenant-Id` 模拟身份：

| 角色 | X-User-Role | 主要权限 |
|---|---|---|
| 管理员 | ADMIN | 全部 |
| 交易员 | TRADER | 策略、交易、组合 |
| 风控 | RISK_OFFICER | 风控规则/预算、盘中、监控 |
| 合规 | COMPLIANCE | 监管、AML、归因、异常交易 |
| 资金 | FUND_MANAGER | 资金、清算、结算 |

```bash
curl -X POST http://localhost:8081/api/strategy/S-1/code \
  -H 'X-User-Role: TRADER' -H 'X-User-Id: trader-1' \
  -H 'Content-Type: application/json' \
  -d '{"sourceCode":"from lhjypt import Strategy\nclass S(Strategy):\n    def on_init(self): pass\n"}'
```

## 6. 回归测试

```bash
cd /Users/link/myApp/lhjypt && mvn -pl core verify   # 345 tests
cd strategy-sdk && python -m pytest tests -q          # 29 passed
cd web && npm run build                               # 类型检查 + 构建
```

## 7. 关键约束

- 多租户：默认 `DEFAULT`，单人可忽略
- 策略上传绑定本机指纹，换机器需重新上传
- 实盘默认 `MOCK`；接真实柜台需补齐适配层
- 行情默认 Mock，真实行情通过 `/api/market/real/refresh` 刷新

## 8. 排障

| 现象 | 处置 |
|---|---|
| 中间件等待超时 | `docker ps` 看 5 容器；必要时 `./scripts/dev.sh up` |
| 后端 8081 起不来 | `./scripts/dev.sh logs backend`；检查端口/DB |
| 前端白屏 | `./scripts/dev.sh logs frontend`；`npm install` |
| 5173 被占用 | 使用 `.env` 的 `LHJYPT_WEB_PORT=5180` |
| 数据重置 | `./scripts/dev.sh reset-db`（清空全部数据） |

更多：[架构](architecture.md) · [部署](deploy.md) · [观测](observability.md) · [交易通道](channel.md) · [接口规划](../.workflow/frontend-api-plan.md)
