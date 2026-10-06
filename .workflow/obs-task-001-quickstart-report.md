# OBS-001 一键部署与使用指南 · 测试报告

> 分支：feature/obs-quickstart · 日期：2026-10-06 · 状态：UTILITY_VERIFIED（单人开箱）

## 1. 交付内容
| 产物 | 说明 |
|------|------|
| `scripts/dev.sh` | 一键运维：`up`（中间件+增量SQL+后端8081+前端5173，幂等）/`status`/`down`（保数据）/`reset-db`/`logs` |
| `scripts/demo.sh` | 一键演示：账户查询→上传策略→水印→沙箱运行(输出)→停止→审计，幂等可重复 |
| `docs/quickstart.md` | 单人开箱指南（启动/目录/角色表/调试/排障） |
| `README.md` | 状态表更新 + 一键入口 |

## 2. 实测验证
| 场景 | 结果 |
|------|------|
| `dev.sh status` | 中间件 5 容器 + 后端 UP + 前端 200 |
| `dev.sh up`（已运行环境重跑） | 幂等复用容器/跳过已起服务，无副作用；修复 compose 与存量容器错位冲突（改「存在即复用」） |
| `dev.sh down/reset-db` | （路径代码化：stop 容器保数据 / rm+重建全量初始化） |
| `demo.sh` | 账户 DEFAULT-001（总资产 999952.43/可用 969215.92）→ 上传加密(AES_256_GCM)→水印 True→沙箱 RUNNING 输出 [demo] init/started/strategy started→STOPPED→审计 3 条（UPLOAD/RUN/STOP） |
| 示例策略 SDK 兼容 | 修正源码为 `from lhjypt.api.strategy import Strategy`（on_init/on_start），沙箱正常执行 |

## 3. 说明
- SQL 全幂等（init-p1/p2 与 p2_001~009 迁移均 IF NOT EXISTS、0 DROP），`up` 可安全重复应用。
- 修复中途发现：compose 元数据与存量容器错位导致 `up -d` 重建冲突 → 改为检测容器存在即复用；`down/reset-db` 改为逐容器管理（不依赖 compose project）。
- STR-DEMO 作为示例资产保留（OFFLINE）；演示脚本幂等，重复执行即复用。

## 4. 结论
单人开箱链路全部验证通过。下一方向：真实交易通道演练（回测→信号→模拟实盘→回报/持仓/资金闭环）。