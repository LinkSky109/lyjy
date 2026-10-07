# OBS-004 交易页持仓/订单轮询 + run-async 根因修复 · 测试报告

> 分支：feature/obs-trading-poll-rapidfix · 日期：2026-10-07 · 状态：VERIFIED

## 1. 前端：交易页持仓/订单自动刷新
- `TradingPage` 查询条件区新增「**自动刷新（10s）**」开关：开启后定时刷新持仓与活跃订单（`positions.reload()` / `activeOrders.reload()`），便于盯盘。
- 浏览器 5173 实测：查询卡/持仓 tab/开关齐全（会话跨天后重新登录验证）。
- `npm run build`（tsc + vite）通过。

## 2. 后端：`/api/backtest/run-async` 挂起根因修复（P0 遗留缺陷）
### 根因（jstack + 运行日志双重实锤）
- `BacktestEngine.generateMockTicks` 用 `BigDecimal` 做随机游走：`price = price.multiply(1+change)`，**scale（小数位）随迭代无限累积膨胀**。
- 迭代到数千次后，`BigDecimal` 运算退化为 `BigInteger.pow`（Toom-cook3 递归超大整数乘法），单 tick 计算从微秒级劣化到数十毫秒级。
- 运行日志实证：修复前一次 `run-async`（10000 ticks）**耗时 291692ms（~292s）**，且该计算发生在 `Controller.generateMockTicks` 内**同步执行**，导致 `run-async` 迟迟不返回 backtestId → HTTP 长时间无响应（此前误判为"进程病态"）。
- 对比：同步 `run` 固定 1000 ticks（膨胀代价小）→ 秒回；因此只 `run-async` 表现挂死。

### 修复
- `generateMockTicks`：游走改 `double` 计算、落盘 `BigDecimal.valueOf(...)` 并 `setScale(8, HALF_UP)`；high/low/turnover 同样限精。**小数位恒 ≤8，杜绝膨胀**。

### 验证
| 项 | 修复前 | 修复后 |
|----|--------|--------|
| 单测（10000 ticks） | —（新增） | **1.24s**，lastPrice/high/turnover scale ≤8 |
| `run-async` 返回 id | ~292s（无响应） | **0.1s** |
| `run-async` 结果 | COMPLETED 292s 后 | 轮询 3s 后 COMPLETED（638 笔） |
| 同步 `run` | 1.7s | 0.08s |
| 全量 `mvn verify` | — | **253/253**（0 跳过，JaCoCo 通过） |

新增测试：`BacktestEngineTest.generateMockTicksKeepsScaleBounded`（精度 + 性能守卫）。

## 3. 说明
- 此前"长运行后端进程病态"判断修正：`/api/backtest/*` 长时间无响应的主因即该 scale 膨胀（10000 ticks 场景）；进程病态与 CLOSE_WAIT 堆积为次要因素，`dev.sh restart` 已补齐兜底运维入口。
- 回测页此前已改走同步 run（交互更快），`run-async` 修复后异步路径亦可用。

## 4. 结论
交易页盯盘轮询就位；`run-async` 挂起为确定性性能缺陷已根修并有单测守护。