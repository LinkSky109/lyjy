# P1 生产化加固（Production Hardening）计划

> 目标：把 P1 交付时以“本地可运行 + 生产适配层预留”形态实现的四项能力，落地为**可切换、可运行时验证**的生产适配实现。保持本地默认行为不变。

## 范围

### 1. 密钥管理生产化（CryptoKeyProvider 可切换）
- `lhjypt.strategy.crypto.provider`：`local`（默认，读 config/key-store.properties）/ `env`（生产由 KMS/Secret 注入环境变量）。
- 新增 `EnvCryptoKeyProvider`：`{prefix}{KEYID}`（Base64 32 字节），缺失即拒绝；KMS/HSM 以同一 SPI 扩展。
- 密钥库文件仍 gitignore；生产由 secret manager 注入，不落库不落日志。

### 2. 容器化策略沙箱（SandboxExecutor 可切换）
- `lhjypt.strategy.sandbox.executor`：`process`（默认）/ `docker`。
- 新增 `DockerSandboxExecutor`：`docker run --rm --network none --memory --cpus --pids-limit`，工作目录挂载 `/sandbox`，SDK 挂载 `/sdk:ro`，`PYTHONUNBUFFERED`，超时 `docker rm -f`，输出回读。
- `SandboxExecutor.outputTail` 统一抽象，服务层不再依赖具体实现。
- macOS Docker Desktop 需 `lhjypt.strategy.sandbox.docker-work-dir` 指向共享目录（Linux 可留空）。

### 3. 分布式调度锁（多实例定时任务互斥）
- 新增 `spring-boot-starter-data-redis` 与 `lhjypt.schedule.lock.*`。
- 新增 `DistributedLock`：Redis `SET NX PX`，`lhjypt:lock:{key}`；抢不到跳过；Redis 异常 fail-open（照常执行，对齐 PRD §10.2）。
- 集成到 `MetricSampler` / `IntradayRiskService` / `TradeRecoveryService` 的 `@Scheduled`。

### 4. 告警通知渠道（Webhook 可配置 + 降级）
- `lhjypt.monitoring.notify.*`：`enabled` / `type(dingtalk|wecom|generic)` / `webhook-url` / `timeout-ms`。
- 新增 `AlertNotifier` + `WebhookAlertNotifier`（JDK HttpClient）/ `LoggingAlertNotifier`（默认仅日志）。
- `AlertEvaluator` 在新告警落库后通知；发送失败仅日志，事件仍可查（对齐 PRD §10.4 通知失败不影响系统）。

## 验证
- `mvn verify`：全量测试 + JaCoCo 门禁。
- 运行时：
  - 以 `LHJYPT_CRYPTO_PROVIDER=env` 上传/下载策略验证环境密钥往返；
  - 以 `LHJYPT_SANDBOX_EXECUTOR=docker` 启动/停止策略，`docker ps` 观测容器与回收；
  - Redis 中观测 `lhjypt:lock:*` 锁键；
  - `LHJYPT_NOTIFY_ENABLED=true` + 本地 webhook 捕获告警 POST 报文。

## 非目标
- 不改变本地默认行为（provider=local、executor=process、notify 关闭时仅日志）。
- KMS/HSM 真实 SDK 接入、容器沙箱镜像治理、真实钉钉/企业微信机器人联调留待生产环境。
