# P3-002 部署常驻与进程守护 · 测试报告

> 分支：feature/p3-002-service · 日期：2026-10-08 · 状态：VERIFIED

## 1. 交付内容
| 产物 | 说明 |
|------|------|
| `scripts/service.sh` | `install/uninstall/status/logs`；macOS 生成 launchd plist（`RunAtLoad` + `KeepAlive=true`，注入 DOCKER_HOST/LH trade mode），Linux 生成 systemd unit（`Restart=always/RestartSec=5`）；Java 路径自动探测（JAVA_HOME→Homebrew openjdk@17→java_home） |
| `scripts/dev.sh` | 新增 `rotate_logs`：单文件 >10MB 归档 `<name>.log.<ts>`，各保留最近 5 个（拉起前后端时执行） |
| `docs/deploy.md` | 常驻安装/自愈验证/日志轮转/dev.sh 与 service.sh 关系 |

## 2. 实测（macOS launchd 真实演练）
| 步骤 | 结果 |
|------|------|
| `service.sh install` | plist 生成于 `~/Library/LaunchAgents/`，launchd `state=running`，健康 **UP** |
| 崩溃自愈 | `kill -9` pid 33782 → 8s 后自动拉起 **pid 33828**，健康 UP |
| 日志轮转 | 造 11M `backend.log` → `dev.sh up` 归档为 `backend.log.20261008-151811`（11M）+ 新日志 |
| `service.sh uninstall` | launchd 服务移除、无残留；`dev.sh up` 恢复运行（健康 UP） |

## 3. 质量门禁
| 项 | 结果 |
|----|------|
| `bash -n`（service.sh / dev.sh） | 通过 |
| 自愈/轮转/卸载 | 全部按预期 |
| 后端 | 无 Java 改动（`mvn` 基线 288/288 不受影响） |

## 4. 说明
- `dev.sh`（开发态，前台日志、易改码）与 `service.sh`（常驻，自愈+自启）互斥占用 8081；install 会先停 dev.sh 实例。
- 常驻日志 `logs/backend-launchd.log` 与 dev 日志同目录统一管理。

## 5. 结论
后端可实现崩溃自愈常驻与日志轮转（P3-002 完成）；单人长期自用具备进程守护能力。