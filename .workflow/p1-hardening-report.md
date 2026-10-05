# P1 生产化加固报告

> 范围：PRD §7.5 / §10.8 的生产化预留项落地为可切换、可运行时验证的实现
> 分支：`feature/p1-hardening`
> 结论：四项能力全部完成并完成运行时验证；另修复 1 个监控链路既有缺陷。

## 1. 密钥管理生产化
- `lhjypt.strategy.crypto.provider`：`local`（默认）/ `env`。
- 新增 `EnvCryptoKeyProvider`：读取 `{LHJYPT_CRYPTO_ENV_PREFIX}{KEYID}`（Base64 32 字节），缺失/长度非法即拒绝；生产由 KMS/Secret 注入环境变量，KMS/HSM 以同一 SPI 扩展。

## 2. 容器化策略沙箱
- `lhjypt.strategy.sandbox.executor`：`process`（默认）/ `docker`。
- 新增 `DockerSandboxExecutor`：`docker run --rm --network none --memory/--cpus/--pids-limit`；工作目录挂载 `/sandbox`，SDK 挂载 `/sdk:ro`；超时 `docker rm -f`；输出回读。
- `SandboxExecutor.outputTail` 统一抽象，服务层不再依赖具体实现。
- `lhjypt.strategy.sandbox.docker-work-dir`：Docker Desktop(macOS) 需指向共享目录，Linux 可留空。

## 3. 分布式调度锁
- 新增依赖 `spring-boot-starter-data-redis` 与 `lhjypt.schedule.lock.*`。
- 新增 `DistributedLock`：Redis `SET key value NX PX`，键 `lhjypt:lock:{task}`；抢不到跳过；Redis 异常 fail-open（照常执行，对齐 PRD §10.2）。
- 集成 `MetricSampler` / `IntradayRiskService` / `TradeRecoveryService` 的 `@Scheduled`。

## 4. 告警通知渠道
- `lhjypt.monitoring.notify.*`：`enabled` / `type(dingtalk|wecom|generic)` / `webhook-url` / `timeout-ms`。
- 新增 `AlertNotifier` + `WebhookAlertNotifier`（JDK HttpClient）/ `LoggingAlertNotifier`（默认仅日志）。
- `AlertEvaluator` 在新告警落库后通知；发送失败仅日志，事件仍可查（PRD §10.4）。

## 5. 缺陷修复
- **监控 ClickHouse 时间参数绑定**：`MetricSampleMapper` 以 `LocalDateTime/Timestamp` 作为查询参数时，ClickHouse JDBC 渲染为未加引号的 `...00:00:00.0`，导致 `metrics/history` 500 且 `AlertEvaluator` 规则评估静默失败。改为按 `yyyy-MM-dd HH:mm:ss` 字符串边界传入（`MetricSampleMapper`）。修复后 `/api/monitor/metrics/history` 恢复 200。

## 6. 验证
### 自动化
| 项 | 结果 |
|----|------|
| `mvn verify` | **176/176** 通过，0 跳过，JaCoCo 门禁通过 |
| Testcontainers schema | 通过 |
| 新增测试 | `EnvCryptoKeyProviderTest` 5、`DockerSandboxExecutorTest` 2（真实 Docker）、`DistributedLockTest` 4、`WebhookAlertNotifierTest` 3、`MetricSamplerTest` 2，并更新 `IntradayRiskServiceTest`/`TradeRecoveryServiceTest`/`AlertEvaluatorTest` |

### 运行时 E2E（真实中间件）
| 开关 | 结果 |
|------|------|
| `LHJYPT_CRYPTO_PROVIDER=env` | 上传/下载策略逐字节往返一致（使用环境注入密钥） |
| `LHJYPT_SANDBOX_EXECUTOR=docker` | 启动 → 容器 `lhjypt-sandbox-s-prod-1`(python:3.11-alpine) RUNNING、输出 `prod sandbox up`；停止 → 容器回收（`docker ps` 为空） |
| Redis 分布式锁 | MONITOR 捕获 `SET lhjypt:lock:risk.intraday.evaluate <id> EX 120 NX` 与释放 `DEL` |
| `LHJYPT_NOTIFY_ENABLED=true` | 规则命中生成告警 `EVT-34f94920`，本地 webhook 收到 `{"metricKey":"e2e.notify.metric","value":"9.00","severity":"WARN",...,"status":"OPEN"}` |

## 7. 兼容性与风险
- 默认配置不变（local / process / notify 关闭），本地零配置可运行。
- Docker Desktop 的挂载需 `docker-work-dir` 指向共享目录（Linux 默认系统临时目录即可）。
- 未做：真实 KMS/HSM SDK、容器镜像治理、真实钉钉/企业微信机器人兼容性、多实例并发抢锁压测（留待生产环境）。
- 回滚：默认开关即可回退；新增类删除、改动类 `git checkout` 可还原。
