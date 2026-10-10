# P4-002 巡检行情新鲜度时区口径 · 任务报告

- 任务：P4-002 bar_time 时区口径（巡检口径修正）
- 依据：`consistency-report-p3.md` §7 已知项「bar_time 时区口径」
- 状态：DONE

## 根因（实测确认）

`bar_time` 以**本地墙钟**写入（应用 `ZoneId.systemDefault()`=Asia/Shanghai），但由 ClickHouse `DateTime`（服务端 UTC）承载，
即 epoch 实际等于"本地墙钟当作 UTC"。因此巡检用 SQL `now()`（真实 UTC）比较时呈现 **-8h 假滞后**：

```
now()=07:35 UTC  now('Asia/Shanghai')=15:35  max(bar_time)=15:00
dateDiff(hour, max_bar, now())                  = -8
dateDiff(hour, max_bar, now('Asia/Shanghai'))   = -16   # tz 参数不改变 instant，且二次偏移
```

业务侧（回测/质量检测）仅做 bar 间相对比较或 Java `LocalDateTime` 同口径，**功能一致**，无需改存量数据。

## 改动

`scripts/healthcheck.sh` 数据面新鲜度查询由 `now()` 改为 `now() + toIntervalSecond(timeZoneOffset(now('<tz>')))`；
时区可配 `LHJYPT_MARKET_TZ`（默认 `Asia/Shanghai`）。`docs/deploy.md` 补充口径说明。

```sql
SELECT if(count()=0, -1,
  dateDiff('hour', max(bar_time),
    now() + toIntervalSecond(timeZoneOffset(now('Asia/Shanghai')))))
FROM lhjypt.bar_data;
```

## 验证

- 修正前：`[OK] ch.bar_data 最新 bar 距今 -8h`（或按 tz 参数误算 -16h）。
- 修正后：`[OK] ch.bar_data 最新 bar 距今 0h`（`healthcheck.sh` 全量 OK=12 WARN=1[前端未启动] FAIL=0）。

## 边界
- 选择"统一到本地口径"而非"迁移存量到 UTC"：单人 Asia/Shanghai 场景，迁移需改动全部查询与存量数据，风险高收益低（P3 报告已判定功能一致）。
- 未改动应用写入逻辑与存量 `bar_data`。
