# TASK-P1-009 策略加密与沙箱测试报告

> 分支：`feature/p1-009-strategy-sandbox`
> 日期：2026-10-05
> 状态：TEST_PASSED

## 1. 自动化测试

| 测试层 | 结果 |
|--------|------|
| Java 单元与集成测试 | 158/158 通过，0 跳过 |
| Testcontainers PostgreSQL schema | 通过（含 `strategy_info`/`strategy_crypto`/`strategy_audit_log`） |
| Python 单元测试 | 23/23 通过 |
| Python 覆盖率 | 85.27% |
| JaCoCo 门禁 | 通过 |

新增用例：`StrategyCryptoServiceTest` 4 例、`StrategySecurityServiceTest` 7 例、
`ProcessSandboxExecutorTest` 2 例、`StrategySchemaTest` 1 例。

## 2. 策略加密与沙箱 E2E

策略源码（`DemoStrategy`，SDK 生命周期 + 自旋）经 6 个接口全链路验证。

| 场景 | 结果 |
|------|------|
| 上传 `/code` | code 0，version=1，checksum=`d9c2…9ecf`，`keyId=local-dev-1`，algo=AES_256_GCM |
| 密文落库 | `strategy_crypto.ciphertext` 为 Base64 密文；`position('Strategy' in ciphertext)=0` 无明文 |
| 下载无令牌 | 业务码 409「需要有效的二次确认令牌」 |
| 下载带令牌 | 解密结果与原文逐字节一致，version=1 |
| 篡改校验和 | 业务码 409「策略校验失败，源码可能被篡改」，拒绝运行 |
| 沙箱启动 | `RUNNING`，pid=42625 |
| 运行态输出 | `outputTail='demo strategy init\ndemo strategy started\n'`（实时无缓冲） |
| 并发启动 | 业务码 409「策略已在运行」 |
| 停止 | `STOPPED`，`strategy_info.status=OFFLINE` |
| 网络沙箱拦截 | `socket.socket()` → `PermissionError: sandbox forbids: socket.__new__`，状态 FAILED |
| 审计查询 | 7 条 UPLOAD/DOWNLOAD/RUN/STOP，`detailJson` 均不含明文 |
| COMPLIANCE 上传 / VIEWER 运行 | HTTP 403 |
| 非归属 TRADER 下载 | 409（先校验确认令牌，二次校验归属） |
| ADMIN 运行/停止 | 放行 |

## 3. 新增能力

- `StrategyCryptoService`：AES-256-GCM（12 字节随机 IV，`Base64(IV‖密文‖Tag)`）+ SHA-256 校验。
- `CryptoKeyProvider` SPI + `LocalCryptoKeyProvider`：读 `config/key-store.properties`，缺失时本地开发兜底密钥；生产 KMS/HSM 预留。密钥文件 `.gitignore`，提供 `.example`。
- `SandboxExecutor` SPI + `ProcessSandboxExecutor`：独立临时目录、`ulimit` CPU/内存、30s 超时强杀、`PYTHONUNBUFFERED` 实时输出、Python 审计钩子白名单（禁网络/子进程/系统调用）、同一策略互斥、单策略崩溃不影响 core。
- `StrategySecurityService` + `StrategySecurityController`：上传/下载/运行/停止/状态/审计 6 接口，归属校验与 `strategy_info` 状态机。
- `AuthInterceptor` 接入 `/api/strategy/**`：写操作仅 TRADER（ADMIN 放行），下载叠加一次性确认令牌。

## 4. 本轮修复与加固

1. 沙箱导入守卫误拦标准库 `shutil`/`socket`（`tempfile`/`urllib` 依赖），导致 SDK 合法导入失败：
   收敛为仅拦 `subprocess/ctypes/multiprocessing/pty`，网络与系统调用改由审计钩子按“操作”拦截。
2. 沙箱监控线程与主动停止存在竞态，可能把 `STOPPED` 覆盖为 `FAILED`：
   `SandboxHandle` 终态改为原子转换（`complete`/`markStopped`）。
3. 强杀丢失缓冲输出：子进程注入 `PYTHONUNBUFFERED=1`，运行中即可读取日志。

## 5. 结论

- 加密落库/下载/校验、沙箱启停与并发互斥、网络隔离、审计留痕、越权拦截全部通过。
- P1-009 满足准出标准，状态流转 `DEV_DONE -> TEST_PASSED`。
- P1 九项任务全部完成，下一步：P1 全量回归 + 四维一致性巡检 + 交付。
