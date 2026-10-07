# OBS-008 SDK 自写策略 × 真实行情回测 · 测试报告

> 分支：feature/obs-008-sdk-real-backtest · 日期：2026-10-07 · 状态：VERIFIED（纯 SDK 交付，后端零改动）

## 1. 交付内容
| 产物 | 说明 |
|------|------|
| `strategy-sdk/lhjypt/backtest/simple_matching.py` | `SimpleMatchingEngine`：bar 级收盘撮合 + 现金/持仓/手续费记账 + 净值统计 |
| `strategy-sdk/lhjypt/backtest/real_data.py` | `load_real_bars()`：从 `/api/market/bars` 拉平台真实 K 线 → `Bar` 序列（升序） |
| `strategy-sdk/lhjypt/examples/real_backtest_demo.py` | 示例：真实 K 线 + 自写策略（DualMaStrategy）+ 本地撮合，输出净值/成交/收益率 |
| 测试 | `test_simple_matching.py` 2 + `test_real_data.py` 2 |

## 2. 实测
| 项 | 结果 |
|----|------|
| SDK 全量 pytest | **27/27** 通过（含新增 4） |
| 示例实跑（BTC_USDT 1h，7 天） | 拉取 **159 根真实 K 线**；策略金叉/死叉信号 **8 笔成交**；equity 1001420.89（**+0.14%**）、手续费按 0.03% 记账 |
| 撮合语义 | 开仓/平仓/限价（仅参考价成立成交）全部覆盖 |

## 3. 说明
- 后端 `mvn verify` 基线维持 264/264（无后端改动；SDK 为 Python 独立交付）。
- 前端回测页保持内置策略路径；自写策略路径由 SDK 提供（与 P0「SDK 回测」架构一致）。

## 4. 结论
用户自写 Python 策略可接入真实行情回测，链路：平台真实 K 线 → SDK 加载 → 策略信号 → 本地撮合 → 净值。真实回测能力完整。