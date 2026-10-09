# P3 一致性校验报告

> 任务：P3 单机生产化与可持续运营（7 任务批次） · 巡检日期：2026-10-09 · 最终状态：CONSISTENCY_PASS

## 1. 五维结果

| 维度 | 结果 | 说明 |
|------|------|------|
| 代码一致性 | 通过 | `mvn verify` 294/294（0 跳过，JaCoCo）+ SDK pytest 29/29；工作区干净 |
| 环境一致性 | 通过 | 5 中间件容器 healthy、后端 200、前端 200；连接池具名（lhjypt-pg/lhjypt-ch） |
| 数据一致性 | 通过 | 备份可恢复到基线（演练）；bar_data 质量 0 缺口/0 异常；关键表齐全 |
| 安全一致性 | 通过 | 密钥接口读写仅 ADMIN；敏感操作确认令牌；熔断/风控告警落库+通知 |
| 文档一致性 | 通过 | p3-plan 7/7；7 份任务报告；6 份 docs（backup/deploy/metrics/security/market-reliability/perf-baseline）；context 同步 |

## 2. 代码一致性

| 检查项 | 结果 |
|--------|------|
| 全量 `mvn verify` | 294/294 通过（0 跳过；含 Testcontainers schema） |
| Python SDK | 29/29 通过 |
| 分支与提交 | P3-001~007 独立分支/提交（`bf4c002`→`812f731`） |
| 工作区 | 干净（无未提交） |

## 3. 环境一致性

| 组件 | 实测 | 结果 |
|------|------|------|
| PostgreSQL / ClickHouse / Kafka / Redis / Zookeeper | healthy（ZK n/a 无 healthcheck，运行中） | 通过 |
| 后端 | 8081 `/actuator/health` 200（db/CH/redis 子项 UP） | 通过 |
| 前端 | 5173 200 | 通过 |
| 连接池 | `hikaricp_connections_max{pool="lhjypt-pg"}=20` / `{pool="lhjypt-ch"}=10` | 通过 |
| 常驻守护 | launchd 安装/kill -9 自愈/卸载 演练通过（任务期） | 通过 |

## 4. 数据一致性

| 检查项 | 结果 |
|--------|------|
| 备份归档 | 204K + sha256 校验 OK |
| 恢复演练（任务期） | PG/CH 恢复到备份基线（行数一致） |
| 关键表 | strategy_info/orders/fund_account/tenant 齐全 |
| 行情质量 | BTC_USDT@1h 303 根：gap 0 / anomaly 0 / duplicate 0 |
| 行情源 | sources=gateio,binance-vision，超时 25s，current=gateio |
| 池压力 | 压测后 active/pending/timeout = 0 |

## 5. 安全一致性

| 检查项 | 结果 |
|--------|------|
| 密钥接口 | `/api/strategy/keys/*` 读+写仅 ADMIN（TRADER 403） |
| 密钥轮换 | 存量重加密 + `KEY_ROTATE` 审计（演练通过） |
| 二次确认 | 下载源码/报送提交/密钥轮换等需 `X-Confirm-Token` |
| 风控告警 | `strategy.circuit`/`portfolio.circuit` CRITICAL 落库 + AlertNotifier |
| 备份排除密钥 | 归档不含 key-store.properties/.env |

## 6. 文档一致性

| 文档 | 状态 |
|------|------|
| `.workflow/p3-plan.md` | 7/7 全部完成 |
| `.workflow/p3-task-001~007-*-report.md` | 全部生成 |
| `docs/backup.md`·`deploy.md`·`metrics.md`·`security.md`·`market-reliability.md`·`perf-baseline.md` | 全部生成 |
| `context.yaml` | p3 7/7、收尾完成 |

## 7. 已知项（不阻断交付）

| 项 | 影响 | 建议 |
|----|------|------|
| bar_time 时区口径：写入为本地时间（+8），ClickHouse `now()` 为 UTC → 巡检新鲜度显示 `-8h` | 仅显示/巡检阈值口径偏差（阈值宽松不影响判定）；回测查询用同口径本地时间，**功能一致** | 后续可统一存 UTC 或在巡检中按 +8 修正 |
| 行情分页翻页回补 | 单次上限 1000 根；更长历史需分批 | P3 后续项 |
| 归档不含密钥 | 恢复后需重新配置密钥 | 文档已注明（`docs/security.md`） |

## 8. 结论

- 代码、环境、数据、安全、文档五个维度全部通过；无阻断项与未闭环问题。
- 状态流转 `CONSISTENCY_PASS`；P3 单机生产化批次可交付。