# OBS-011 策略参数优化（真实行情网格寻优） · 测试报告

> 分支：feature/obs-011-optimizer · 日期：2026-10-07 · 状态：VERIFIED

## 1. 交付内容
| 产物 | 说明 |
|------|------|
| `StrategyOptimizerService` | 真实行情上双均线（快/慢周期）网格寻优：读库一次取真实 K 线（ohlc/close 模式）→ 注入参数化 `BuiltInStrategy` → 逐组合回测 → 目标排序（夏普/总收益/卡玛） |
| `OptimizeRequest/Result` | 网格/目标/回撤上限/组合数防爆；结果含 best + 全组合排序 + 基准 5/20 对照 + 被过滤数 |
| `BacktestEngine.run(config, ticks, strategy)` | 引擎支持注入参数化策略（默认构造兼容） |
| API | `POST /api/backtest/optimize` |
| 前端 | 回测页新增「策略参数优化」卡片：网格输入、目标选择、结果表（最优高亮 + 基准对照） |

## 2. 实测（真实 BTC_USDT 1h 行情）
| 目标 | 结果 |
|------|------|
| SHARPE（7 天，7 组合） | 排序/最优/Baseline 齐全（短期夏普全 0 无分化——如实） |
| TOTAL_RETURN（30 天，16 组合） | **best=10/20：ret -0.297%、dd 0.33%**，优于基准 5/20（-0.361%、dd 0.43%）；top6 梯度清晰 |

## 3. 质量门禁
| 项 | 结果 |
|----|------|
| `StrategyOptimizerServiceTest` | 3/3（排序+最优+基准；回撤过滤；非法参数排除） |
| 全量 `mvn verify` | **271/271**（0 跳过，JaCoCo 通过） |
| 前端 `npm run build` | 通过 |

## 4. 说明
- slow<=fast 非法参数排除（不参与寻优）；回撤超限组合过滤并计数。
- 优化基于当前真实行情存量（202 根 1h）；长期/多频段优化需先扩大 refresh 窗口。

## 5. 结论
策略参数优化闭环：真实行情 → 网格回测 → 目标寻优 → 最优参数与基准对照；前端一键使用。