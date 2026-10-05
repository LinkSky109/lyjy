# TASK-P1-009 策略加密与沙箱执行计划

> 分支：`feature/p1-009-strategy-sandbox`
> 需求：`docs/p1-prd.md` §5.8、§6，表 `strategy_info` / `strategy_crypto` / `strategy_audit_log`
> 目标：策略源码 AES-256-GCM 加密存储、下载二次确认与审计、受限于进程沙箱的运行与停止、全程审计留痕。

## 节点

1. 实体 `StrategyInfo` / `StrategyCrypto` / `StrategyAuditLog` 与 Mapper。
2. `CryptoKeyProvider` SPI + `LocalCryptoKeyProvider`（`config/key-store.properties`，缺失时本地开发兜底密钥），生产 KMS/HSM 预留。
3. `StrategyCryptoService`：AES-256-GCM 加解密 + SHA-256 校验。
4. `SandboxExecutor` SPI + `ProcessSandboxExecutor`：受限子进程（ulimit CPU/内存、30s 超时强杀、工作目录隔离、Python 审计钩子白名单、输出捕获）。
5. `StrategySecurityService`：上传/下载/运行/停止/状态/审计编排，策略归属与状态机。
6. `StrategySecurityController` 6 接口；`AuthInterceptor` 接入 `/api/strategy/**` 权限。
7. 单元测试 + 真实 E2E（加密落库、下载、校验失败拒绝、沙箱启停、审计查询）。

## 业务规则

- 加密：`AES/GCM/NoPadding`，12 字节随机 IV，输出 `Base64(IV‖密文‖Tag)`；`key_id` 与密钥分离。
- 校验：上传记录源码 SHA-256；加载/下载时重算比对，不一致拒绝。
- 明文边界：明文仅在进程内；日志、审计、列表接口永不输出明文；只有携带 `X-Confirm-Token` 的下载接口向归属人返回源码。
- 审计动作：`UPLOAD` / `DOWNLOAD` / `RUN` / `STOP` / `EDIT_PARAM`，保留 ≥3 年，不记录明文。
- 沙箱：单策略不并发运行；超时 30s 强杀；单策略崩溃不影响 core；`SandboxExecutor` 可切换容器实现（生产）。
- 状态机：`strategy_info.status` `OFFLINE → RUNNING → OFFLINE`，异常 `ERROR`。
- 归属：TRADER 仅能操作/查看自属策略；ADMIN 可操作；COMPLIANCE 可查全部审计。

## 接口

| 方法 | 路径 | 权限 |
|------|------|------|
| POST | `/api/strategy/{strategyId}/code` | TRADER（自属）/ ADMIN |
| POST | `/api/strategy/{strategyId}/code/download` | TRADER（自属）+ 确认口令 |
| POST | `/api/strategy/{strategyId}/run` | TRADER（自属）/ ADMIN |
| POST | `/api/strategy/{strategyId}/stop` | TRADER（自属）/ ADMIN |
| GET | `/api/strategy/audit?strategyId=&action=` | COMPLIANCE / TRADER 自属 |
| GET | `/api/strategy/sandbox/status/{strategyId}` | TRADER 自属 / ADMIN |

## 验收

- 上传后 `strategy_crypto.ciphertext` 为密文、库内无明文；`checksum` 为源码 SHA-256。
- 下载需有效确认令牌；解密结果与原文一致；无令牌 409。
- 篡改密文后校验失败拒绝运行。
- 沙箱启动后状态 RUNNING，进程真实存在；停止后 OFFLINE 且进程回收。
- 相同策略并发启动被拒绝（服务端锁）。
- 审计按动作/策略可查；审计内容不含明文源码。
- TRADER 越权访问他人策略被拒绝。
