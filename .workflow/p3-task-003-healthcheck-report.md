# P3-003 启动自检与健康巡检 · 测试报告

> 分支：feature/p3-003-healthcheck · 日期：2026-10-09 · 状态：VERIFIED

## 1. 交付内容
| 产物 | 说明 |
|------|------|
| `scripts/healthcheck.sh` | 巡检：中间件 5 容器健康 → 后端健康与子组件（PG/CH/Redis）→ 前端可达 → 数据面（关键表齐全 / bar_data 新鲜度 ≤24h / 磁盘 <90%）；stdout + 追加 `logs/healthcheck.log`；`FAIL>0` 退出码 1；可选 `LHJYPT_HEALTH_WEBHOOK` 异常通知 |
| `docs/deploy.md` | 巡检用法、定时（launchd/cron）示例、常见处置表 |
| `scripts/dev.sh` | 修复：容器存在但**停止**（如机器重启）时 `docker start` 拉起，而非等待健康超时 |

## 2. 实测
### 正常（13 项）
```
[OK] 5 容器 healthy · backend + primaryDataSource/clickHouseDataSource/redis UP
[OK] frontend 200 · pg.tables 齐全 · ch.bar_data 距今 7h · disk 6%
健康巡检: OK=13 WARN=0 FAIL=0   exit=0
```
### 异常（停后端）
```
[FAIL] backend 8081 未响应或无 UP
健康巡检: OK=9 WARN=0 FAIL=1    exit=1   （日志已追加）
```
恢复后端后再次巡检回到全 OK。

## 3. 过程中的环境/脚本修复
- **Lima VM 停止**（跨天）：容器处于 Exited → `docker start` 恢复；`dev.sh` 增加「存在即 start」逻辑（此前会等待健康超时）。
- 脚本中 `$var`（中文紧邻）在非 UTF-8 locale 下被并入变量名 → 统一 `${var}`。

## 4. 质量门禁
| 项 | 结果 |
|----|------|
| `bash -n`（healthcheck.sh / dev.sh） | 通过 |
| 正常/异常巡检 | 退出码与报告符合预期 |
| 后端 | 无 Java 改动（`mvn` 基线 288/288 不受影响） |

## 5. 结论
健康巡检可用（含数据面与磁盘），异常退出码可被定时任务捕获并通知（P3-003 完成）。