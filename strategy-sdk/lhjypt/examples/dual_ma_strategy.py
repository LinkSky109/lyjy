"""
示例策略：双均线交叉策略
短期均线上穿长期均线买入，下穿卖出
"""
from lhjypt import Strategy
from lhjypt.indicators import SMA


class DualMaStrategy(Strategy):
    """双均线策略

    参数：
        fast_period: 短期均线周期，默认5
        slow_period: 长期均线周期，默认20
    """

    fast_period = 5
    slow_period = 20

    def on_init(self):
        self.closes = []
        self.position = 0
        self.subscribe("rb2410")
        self.context.log("双均线策略初始化，快周期=%d 慢周期=%d"
                         % (self.fast_period, self.slow_period))

    def on_bar(self, bar):
        self.closes.append(bar["close"])

        if len(self.closes) < self.slow_period:
            return

        fast_ma = SMA(self.closes, self.fast_period)[-1]
        slow_ma = SMA(self.closes, self.slow_period)[-1]

        if fast_ma is None or slow_ma is None:
            return

        # 金叉买入
        if fast_ma > slow_ma and self.position == 0:
            self.buy(bar["symbol"], 1, bar["close"])
            self.position = 1
            self.context.log("金叉买入 @ %.2f" % bar["close"])

        # 死叉卖出
        elif fast_ma < slow_ma and self.position > 0:
            self.sell(bar["symbol"], self.position, bar["close"])
            self.position = 0
            self.context.log("死叉卖出 @ %.2f" % bar["close"])
