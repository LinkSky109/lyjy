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

## 健康巡检（P3-003）

```bash
./scripts/healthcheck.sh                                 # 巡检（异常退出码 1，便于定时任务捕获）
LHJYPT_HEALTH_WEBHOOK=<webhook-url> ./scripts/healthcheck.sh   # 异常时 POST 通知
```

巡检项：中间件 5 容器健康 → 后端健康与子组件（PG/CH/Redis）→ 前端可达 → 数据面（关键表齐全 / bar_data 新鲜度 ≤24h / 磁盘 <90%）。
报告输出到 stdout 并追加 `logs/healthcheck.log`；`FAIL>0` 时退出码 1。

### 定时巡检（示例）

macOS（launchd，每 10 分钟）：
```xml
<!-- ~/Library/LaunchAgents/com.lhjypt.healthcheck.plist -->
<key>ProgramArguments</key><array>
  <string>/bin/bash</string><string>-lc</string>
  <string>/Users/link/myApp/lhjypt/scripts/healthcheck.sh</string>
</array>
<key>StartInterval</key><integer>600</integer>
```
（`launchctl bootstrap gui/$(id -u) <plist>`）

Linux（cron）：
```
*/10 * * * * /path/to/lhjypt/scripts/healthcheck.sh >> /path/to/lhjypt/logs/healthcheck.log 2>&1
```

### 常见处置

| 巡检 FAIL/WARN | 处置 |
|----------------|------|
| 容器 missing/exited | `./scripts/dev.sh up`（会自动 `docker start` 已存在容器） |
| backend 未响应 | `./scripts/dev.sh up` 或 `./scripts/service.sh status`（若用常驻） |
| ch.bar_data 滞后 >24h | `POST /api/market/real/refresh`（或检查定时刷新开关） |
| disk ≥90% | 清理 `backups/`、`logs/*.log.*`、ClickHouse 旧分区 |
