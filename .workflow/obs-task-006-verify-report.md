# OBS-006 定时行情刷新 + 真实 K 线回测 · 测试报告

> 分支：feature/obs-006-real-refresh-backtest · 日期：2026-10-07 · 状态：VERIFIED

## 1. 定时行情刷新（自动拉取）
- `RealMarketProperties.refreshIntervalMs`（默认 900000=15min，`0` 关闭，env 可配）。
- `RealMarketDataService.scheduledRefresh()`：`@Scheduled` + `DistributedLock`（防重）+ 失败静默（外网抖动不中断调度）；`status` 增 `autoRefreshMs`。
- 实测（临时 8s 间隔）：`autoRefreshMs=8000` 生效、`lastRefreshAt` 自动更新、去重新增 0（数据已全量）→ 定时链路正确。
- 单测：失败静默（抛错不传）、正常路径写库、interval=0/enabled=false 跳过（3 例）。构造器新增 DistributedLock（mock stub 直接运行任务）。

## 2. 真实 K 线回测
- `BarTicksMapper`：ClickHouse `bar_data` 真实 K 线 → 每根展开 O/H/L/C 4 个参考 tick（时间毫秒递增、量/额均分）。
- `POST /api/backtest/run-real?frequency=`：读真实 bar（空 → 422 提示先 refresh）→ 转 ticks → 引擎撮合。
- 前端「回测」页新增「真实行情回测」按钮（复用表单，频率默认 1h）。
- 实测：BTC_USDT 1h 最近 2 天真实回测 **COMPLETED，5 笔交易，totalReturn -0.76%**（基于真实 K 线）。

## 3. 质量门禁
| 项 | 结果 |
|----|------|
| 新测试 | `BarTicksMapperTest` 2 + `RealMarketDataServiceTest` 定时 3（共 6） |
| 全量 `mvn verify` | **263/263**（0 跳过，JaCoCo 通过） |
| 前端 | `npm run build` 通过（两处 variant 修正） |

## 4. 说明
- 回测命中真实行情的前提是 `refresh`（手动或定时）已写入 bar_data；数据窗取 body 的 startTime/endTime。
- 真实回测为 tick 级引擎按 bar 展开撮合（近似），完整信号级别回测留后续优化。

## 5. 结论
真实行情自动拉取 + 真实数据回测全部落地：行情从"手动刷新"到"自动维护"，回测从"Mock 数据"到"真实 K 线"。