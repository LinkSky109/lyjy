"""RSI 均值回归示例策略（可直接用于回测/沙箱）。

规则：
- RSI 低于 oversold：空仓买入
- RSI 高于 overbought：持多卖出
- 单标的、固定 1 手，避免示例策略引入仓位管理复杂度
"""
from lhjypt import Strategy
from lhjypt.indicators import RSI


class RsiReversionStrategy(Strategy):
    symbol = "BTC_USDT"
    rsi_period = 14
    oversold = 30.0
    overbought = 70.0
    qty = 1

    def on_init(self):
        self.closes = []
        self.position = 0
        if self.context is not None:
            self.subscribe(self.symbol)
        self._log(
            "RSI 策略初始化: %s period=%d 超卖=%.0f 超买=%.0f"
            % (self.symbol, self.rsi_period, self.oversold, self.overbought)
        )

    def _log(self, message):
        if self.context is not None:
            self.context.log(message)

    def on_bar(self, bar):
        self.closes.append(float(bar["close"]))
        if len(self.closes) < self.rsi_period + 1:
            return
        rsi = RSI(self.closes, self.rsi_period)[-1]
        if rsi is None:
            return

        if rsi < self.oversold and self.position == 0:
            self.buy(bar["symbol"], self.qty, bar["close"])
            self.position = self.qty
            self._log("RSI=%.2f 超卖买入 @ %.2f" % (rsi, bar["close"]))
        elif rsi > self.overbought and self.position > 0:
            self.sell(bar["symbol"], self.position, bar["close"])
            self.position = 0
            self._log("RSI=%.2f 超买卖出 @ %.2f" % (rsi, bar["close"]))
