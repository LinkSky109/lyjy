# 数据备份与恢复（P3-001）

> 单人自用最关心「数据不丢」。本页覆盖 PG + ClickHouse 关键表 + 配置的备份、校验与恢复。

## 一键操作

```bash
./scripts/backup.sh backup                 # 备份 → backups/lhjypt-backup-<ts>.tar.gz + .sha256
./scripts/backup.sh list                   # 列出备份
./scripts/backup.sh verify <archive>       # 校验归档 sha256
./scripts/backup.sh restore <archive>      # 恢复（自动停后端，恢复后手动 dev.sh up）
```

## 归档内容

| 文件 | 内容 |
|------|------|
| `pg.sql.gz` | PostgreSQL 全库逻辑备份（pg_dump） |
| `ch-bar_data.native.gz` | ClickHouse 行情 K 线（Native，可回灌） |
| `ch-tick_data.native.gz` | 逐笔（Native；空表跳过） |
| `ch-metric_sample.native.gz` | 监控指标采样（Native） |
| `config.tar.gz` | `config/`（排除密钥文件与应用 .env）、`scripts/`、`application.yaml`、quickstart |
| `manifest.txt` | 备份时间 + 关键表行数 + 各文件 sha256 |

## 恢复行为

1. 校验归档 sha256；
2. 停止后端（PG 恢复需独占连接）；
3. `DROP SCHEMA public CASCADE; CREATE SCHEMA public;` 后导入 `pg.sql.gz`；
4. ClickHouse 各关键表 `TRUNCATE` 后回灌（空表自动跳过）；
5. 输出恢复前的行数对照，提示 `./scripts/dev.sh up` 重启。

> 注意：恢复会**覆盖当前数据库**；执行前请确认归档正确（`verify`）。

## 推荐节奏（单人）

- 重要变更（升级/批量导入/优化落档）前后各备份一次；
- 每日一次 `backup`（可加入 crontab/launchd，P3-002 常驻任务可承接）；
- 归档保留于 `backups/`（已 gitignore）；如需异地保存，复制到网盘/移动盘即可。

## 演练记录（2026-10-08）

| 步骤 | 结果 |
|------|------|
| 备份 | 173K 归档 + sha256 |
| 破坏 | 删 PG 表数据 + TRUNCATE bar_data |
| 恢复 | PG 重建导入 + CH 回灌（空表跳过） |
| 校验 | tenants 1 / strategies 3 / aml_rules 2 / opt_archive 6 / bar_data 788 / metric_sample 74475 —— **与备份基线完全一致** |