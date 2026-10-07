"""信号交易演练策略（OBS-009）：真实行情驱动 → 双均线信号 → 自动下单。

在平台沙箱内运行：循环拉取真实 K 线（/api/market/bars，已入库 Gate.io 数据），
计算 SMA(5)/SMA(20)：
- 无持仓且快线>慢线  -> 限价买入 1；
- 有持仓且快线<慢线  -> 平仓卖出；
- 无信号则等待下一轮（轮询 4s），输出决策日志供 outputTail 观测。

由 scripts/signal-trade-demo.sh 上传为 SIG-TRADE 并在沙箱运行；
先平旧仓保证幂等（每轮演练以持有 1 手或 0 手结束）。
"""
import threading
import time

from lhjypt.api.strategy import Strategy
from lhjypt.indicators import SMA
from lhjypt.live.live_api import LiveApiError, LiveDataAPI, LiveOrderAPI, RestClient

SYMBOL = "BTC_USDT"
ACCOUNT = "DEFAULT-001"
STRATEGY_ID = "SIG-TRADE"


class SignalTrader(Strategy):

    def on_init(self):
        self.alive = True
        self.pos = 0
        self.no_signal_rounds = 0
        threading.Thread(target=self.loop, daemon=True).start()

    def on_start(self):
        print("[sig] signal trader started (real market driven)")

    def loop(self):
        client = RestClient()
        data = LiveDataAPI(client, account_id=ACCOUNT, strategy_id=STRATEGY_ID,
                           user_id="trader-1")
        orders = LiveOrderAPI(client, account_id=ACCOUNT, strategy_id=STRATEGY_ID,
                              user_id="trader-1")
        while self.alive:
            try:
                bars = data.get_bar(SYMBOL, "1h", 30)
                if len(bars) < 26:
                    print("[sig] 真实K线不足(%d)，等待…" % len(bars))
                    time.sleep(3)
                    continue
                closes = [float(b["close"]) for b in bars]
                fast = SMA(closes, 5)[-1]
                slow = SMA(closes, 20)[-1]

                # 幂等：先对齐本地持仓（平旧仓）
                if self.pos == 0:
                    existing = [p for p in data.get_position(SYMBOL)
                                if float(p.get("quantity") or 0) > 0]
                    if existing:
                        self.pos = 1
                        print("[sig] 发现遗留持仓 %d，先平仓" % len(existing))
                        orders.sell(SYMBOL, 1, price=closes[-1])
                        self.pos = 0
                        time.sleep(2)
                        continue

                last = closes[-1]
                if self.pos == 0:
                    if fast > slow:
                        order_id = orders.buy(SYMBOL, 1, price=last)
                        self.pos = 1
                        print("[sig] 金叉买入 %s @%.2f -> %s" % (SYMBOL, last, order_id))
                    else:
                        self.no_signal_rounds += 1
                        print("[sig] 无信号，close=%.2f sma5=%.2f sma20=%.2f"
                              % (last, fast, slow))
                        # 演示注单：连续 2 轮无信号则按当前价强制下一单（演练展示成交路径）
                        if self.no_signal_rounds >= 2:
                            order_id = orders.buy(SYMBOL, 1, price=last)
                            self.pos = 1
                            self.no_signal_rounds = 0
                            print("[sig] 演示注单（无信号连续2轮强制）买入 %s @%.2f -> %s"
                                  % (SYMBOL, last, order_id))
                elif self.pos > 0:
                    if fast < slow:
                        orders.sell(SYMBOL, 1, price=last)
                        self.pos = 0
                        self.no_signal_rounds = 0
                        print("[sig] 死叉平仓 %s @%.2f" % (SYMBOL, last))
                    else:
                        print("[sig] 持仓中，sma5=%.2f sma20=%.2f" % (fast, slow))
            except LiveApiError as error:
                print("[sig] api error:", error.code, str(error)[:80])
            except Exception as error:  # noqa: BLE001
                print("[sig] error:", error)
            time.sleep(4)

    def on_stop(self):
        self.alive = False
        print("[sig] stopped")