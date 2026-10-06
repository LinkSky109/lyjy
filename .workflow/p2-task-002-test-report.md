# TASK-P2-002 策略水印与运行环境绑定测试报告

> 分支：`feature/p2-002-strategy-watermark` · 日期：2026-10-06 · 状态：TEST_PASSED

## 1. 自动化测试
| 测试层 | 结果 |
|--------|------|
| Java 单元与集成测试 | **209/209** 通过，0 跳过 |
| Testcontainers schema | 通过（含 p2_001/p2_002 迁移与 34+5 张表） |
| JaCoCo 门禁 | 通过 |

新增：`StrategyWatermarkServiceTest` 6、`StrategySecurityServiceTest` 更新为 9（含水印/环境绑定/溯源用例）、`RegulatorySchemaTest` 扩展。

## 2. E2E（真实 PG）
| 场景 | 结果 |
|------|------|
| 上传 S-WM-1 | code 0，version 1；`strategy_watermark` 登记 token=9ae8…、指纹=c853… |
| 查看水印 | `watermarked=true`、owner=wm-owner |
| 下载源码 | 含 `# lhjypt-watermark-<64hex>` 标记 |
| 溯源（真实令牌副本） | `found=true`，strategyId=S-WM-1、owner=wm-owner、uploadedBy=wm-owner |
| 溯源（伪造/无标记） | `found=false` |
| 环境绑定运行 | 同宿主指纹 → RUNNING → 停止 |
| 权限 | TRADER 调用 `/trace` → HTTP 403 |

## 3. 新增能力
- `strategy_watermark`：策略/版本/令牌/作者/上传者/源码哈希/环境指纹，令牌唯一。
- 水印：SHA-256(策略|作者|上传时间) → 标记行；从任意副本可提取令牌并溯源（合规/管理员）。
- 环境绑定：跑前比对宿主指纹，不一致拒绝运行（`lhjypt.strategy.security.env-binding-enabled` 可关）。
- 接口：策略水印查看 + 泄露溯源。

## 4. 修复
水印正则原先只截取 32 位十六进制，而令牌为 64 位，导致溯源查不到登记行；改为 `{64}` 后复验通过。

## 5. 结论
打水印、密文含标、副本溯源、环境绑定、越权拦截全部通过。P2-002 满足准出。下一任务：P2-003 风险预算分配与占用监控。