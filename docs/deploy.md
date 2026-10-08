# 常驻部署与进程守护（P3-002）

> 目标：后端作为**常驻服务**运行——崩溃自动拉起、开机/登录自启、日志集中且轮转。

## 一键托管

```bash
./scripts/service.sh install     # 安装为常驻服务（macOS launchd / Linux systemd）
./scripts/service.sh status      # 常驻状态 + 健康检查
./scripts/service.sh logs        # 跟踪日志
./scripts/service.sh uninstall   # 卸载（停止常驻）
```

### macOS（launchd）
- 生成 `~/Library/LaunchAgents/com.lhjypt.backend.plist`：`RunAtLoad` + `KeepAlive=true`（崩溃/退出即拉起），工作目录为本项目，注入 `DOCKER_HOST`（Lima）与 `LHJYPT_TRADE_MODE=MOCK`。
- 日志：`logs/backend-launchd.log`。
- Java 路径自动探测：`JAVA_HOME` → Homebrew `openjdk@17` → `/usr/libexec/java_home -v 17`。

### Linux（systemd）
- `./scripts/service.sh install` 生成 `/tmp/lhjypt-backend.service`（`Restart=always` + `RestartSec=5`），按提示 `sudo cp` 到 `/etc/systemd/system/` 后 `systemctl enable --now lhjypt-backend`。

## 崩溃自愈验证

```bash
./scripts/service.sh install
kill -9 "$(lsof -ti:8081)"      # 模拟崩溃
sleep 6 && ./scripts/service.sh status   # 应自动拉起且健康 UP
./scripts/service.sh uninstall
```

## 日志轮转

`dev.sh` 在拉起前后端时自动轮转：单文件超过 **10MB** 归档为 `<name>.log.<时间戳>`，各保留最近 **5** 个（`rotate_logs`）。
`service.sh` 常驻日志（`backend-launchd.log`）由同一目录管理，可按需手动清理或纳入定时任务（P3-003）。

## 与 dev.sh 的关系

| 场景 | 用法 |
|------|------|
| 开发/调试（前台日志、可随时改代码） | `./scripts/dev.sh up` / `restart` |
| 长期自用（常驻、崩溃自愈、开机自启） | `./scripts/service.sh install` |

两者互斥（都占用 8081）；`service.sh install` 会先停掉 `dev.sh` 启动的实例。
