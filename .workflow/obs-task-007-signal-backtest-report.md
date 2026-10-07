# OBS-007 信号级真实回测优化 · 测试报告

> 分支：feature/obs-007-signal-level-backtest · 日期：2026-10-07 · 状态：VERIFIED

## 1. 交付内容
- `BarTicksMapper.fromBars(bars, mode)`：两种信号模式
  - `ohlc`（默认）：每根 bar 展开 O/H/L/C 4 个参考 tick（保留已有行为）
  - `close`：每根 bar 仅 1 个收盘价 tick——贴近收盘信号策略（均线交叉在收盘确认），消除 bar 内 O/H/L 顺序伪影
- `POST /api/backtest/run-real?mode=close|ohlc`（mode 参数，默认 ohlc 兼容）
- 前端回测页「真实行情回测」旁新增「**收盘信号（close-only）**」勾选

## 2. 实测对照（BTC_USDT 真实 1h K 线）
| 模式 | 窗口 | 结果 |
|------|------|------|
| ohlc | 2 天 | COMPLETED 5 笔，totalReturn -0.76% |
| close | 2 天 | COMPLETED 0 笔（短期窗口双均线未交叉——信号差异的真实反映） |
| close | 7 天 | COMPLETED **10 笔**，totalReturn -3.61%，winRate 0%（收盘交叉信号正常撮合，真实行情下策略表现如实） |

## 3. 质量门禁
| 项 | 结果 |
|----|------|
| `BarTicksMapperTest` 增 close 模式用例 | 3/3 通过 |
| 全量 `mvn verify` | **264/264**（0 跳过，JaCoCo 通过） |
| 前端 `npm run build` | 通过 |

## 4. 说明
close 模式 2 天 0 笔并非缺陷，而是"收盘交叉在短期无触发"的真实结果；7 天窗口验证撮合链路正常。实际信号质量由策略/数据决定，平台如实呈现。

## 5. 结论
真实回测支持两种信号粒度，收盘信号模式消除 bar 内伪影；真实行情回测链路完整。