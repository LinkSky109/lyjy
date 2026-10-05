"""
示例策略：网格交易策略
在价格区间内设置多个网格，价格下跌买入，上涨卖出
"""
from lhjypt import Strategy


class GridStrategy(Strategy):
    """网格交易策略

    参数：
        grid_num: 网格数量
        lower_price: 区间下限
        upper_price: 区间上限
    """

    grid_num = 10
    lower_price = 3000
    upper_price = 4000

    def on_init(self):
        self.grid_step = (self.upper_price - self.lower_price) / self.grid_num
        self.grids = [self.lower_price + i * self.grid_step
                      for i in range(self.grid_num + 1)]
        self.grid_position = {}
        self.subscribe("rb2410")
        self.context.log("网格策略初始化，区间[%d,%d]，%d格"
                         % (self.lower_price, self.upper_price, self.grid_num))

    def on_bar(self, bar):
        price = bar["close"]
        symbol = bar["symbol"]

        for i, grid_price in enumerate(self.grids):
            # 价格跌破网格线买入
            if price <= grid_price and not self.grid_position.get(i):
                self.buy(symbol, 1, price)
                self.grid_position[i] = True
                self.context.log("网格买入 第%d格 @ %.2f" % (i, price))

            # 价格涨回上一网格线卖出
            elif (i > 0 and price >= self.grids[i - 1]
                  and self.grid_position.get(i)):
                self.sell(symbol, 1, price)
                self.grid_position[i] = False
                self.context.log("网格卖出 第%d格 @ %.2f" % (i, price))
