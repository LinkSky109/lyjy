# OBS-010 策略自动值守调度 · 测试报告

> 分支：feature/obs-010-watchdog · 日期：2026-10-07 · 状态：VERIFIED

## 1. 交付内容
| 产物 | 说明 |
|------|------|
| `StrategyWatchdogService` | @Scheduled 值守扫描（DistributedLock 防重）：运行中跳过；离线且过冷却 → 系统上下文自动拉起；失败记 lastError 并冷却 |
| `WatchdogEntry` 模型 | 值守条目（enabled/checkIntervalMs/lastStatus/lastError，标准 bean 序列化） |
| `StrategyWatchdogController` | `POST /api/strategy/{id}/watchdog?enabled&checkIntervalMs` · `GET /api/strategy/watchdog/list`（归属校验：非 ADMIN 仅本策略） |
| `StrategySecurityService.requireStrategyOwnerOrAdmin` | 值守/系统场景复用归属校验 |
| 前端策略页 | 运行 tab「开启/关闭自动值守」按钮 |
| 配置 | `lhjypt.strategy.watchdog.enabled/scan-interval-ms` |

## 2. 实测（真实 E2E）
| 步骤 | 结果 |
|------|------|
| 上传 WD-1 + 开启值守（冷却 5s） | code=0，enabled=True |
| run → stop（制造离线） | RUNNING → STOPPED |
| 自动拉起 | **~18s（一个 scan 周期内）自动拉起 → RUNNING** |
| list 序列化 | `{strategyId, enabled, checkIntervalMs, lastStatus, lastError}` 完整 |
| 关闭值守 | enabled=False |

日志佐证：`策略值守拉起 WD-1 -> RUNNING`（system 上下文 RUN 审计）。

## 3. 过程中的缺陷修复
1. **YAML 重复键**：watchdog 配置误加独立 `strategy:` 顶层节点，与既有 `strategy.security` 冲突致启动失败 → 并入已有 `strategy:` 节点。
2. **WatchdogEntry 不可序列化**：方法式（`strategyId()`）非 JavaBean getter → Jackson 500 → 改标准 getter/setter。
3. **`@Scheduled fixedRateString=0` 触发 IllegalArgumentException 启动失败**（本系列"后端反复消失"的根因）→ 0 不再作为关闭语义；定时关闭用 `enabled=false`，间隔下限 5000（配置注释与 guard 同步）。

## 4. 说明
- 值守为内存态（重启失效），持久化方式：把启用项写入 `application.yaml`/环境变量（文档说明）。
- 进程沙箱约 60s 超时强杀长驻策略，值守保证"被杀即重启"（挂机续命）；沙箱内状态在重启间不跨进程保留（受控行为）。

## 5. 质量门禁
| 项 | 结果 |
|----|------|
| `StrategyWatchdogServiceTest` | 4/4（运行跳过/冷却拉起/失败冷却/门禁归属） |
| 全量 `mvn verify` | **268/268**（0 跳过，JaCoCo 通过） |
| 前端 `npm run build` | 通过 |

## 6. 结论
策略自动值守落地：挂机运行、离线自拉起、失败冷却、归属防护。单人"让策略自动跑"闭环。