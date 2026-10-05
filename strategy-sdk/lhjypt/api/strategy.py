"""
策略基类 - 所有用户策略必须继承此类
"""
from abc import ABC, abstractmethod
from typing import Optional
from ..engine.event import EventEngine


class Strategy(ABC):
    """策略基类

    用法示例:
        class MyStrategy(Strategy):
            def on_init(self):
                self.subscribe("rb2410")

            def on_bar(self, bar):
                if bar.close > bar.open:
                    self.buy("rb2410", 1, bar.close)
    """

    def __init__(self):
        self.context: Optional["Context"] = None
        self._event_engine: Optional[EventEngine] = None
        self.strategy_id: str = ""
        self.strategy_name: str = ""

    def set_context(self, context: "Context"):
        """注入运行上下文（平台内部调用）"""
        self.context = context

    def set_event_engine(self, engine: EventEngine):
        """注入事件引擎（平台内部调用）"""
        self._event_engine = engine

    # ===== 生命周期回调 =====

    @abstractmethod
    def on_init(self):
        """策略初始化回调，订阅标的、加载参数在此完成"""
        pass

    def on_start(self):
        """策略启动回调"""
        pass

    def on_stop(self):
        """策略停止回调"""
        pass

    # ===== 行情回调 =====

    def on_tick(self, tick):
        """tick行情回调（可选实现）"""
        pass

    def on_bar(self, bar):
        """K线行情回调（可选实现）"""
        pass

    def on_order(self, order):
        """订单状态变更回调（可选实现）"""
        pass

    def on_trade(self, trade):
        """成交回报回调（可选实现）"""
        pass

    # ===== 交易接口 =====

    def buy(self, symbol: str, volume: float, price: float = None):
        """买入开仓"""
        return self.context.order_api.buy(symbol, volume, price)

    def sell(self, symbol: str, volume: float, price: float = None):
        """卖出平仓"""
        return self.context.order_api.sell(symbol, volume, price)

    def short(self, symbol: str, volume: float, price: float = None):
        """卖出开仓（期货）"""
        return self.context.order_api.short(symbol, volume, price)

    def cover(self, symbol: str, volume: float, price: float = None):
        """买入平仓（期货）"""
        return self.context.order_api.cover(symbol, volume, price)

    def cancel_order(self, order_id: str):
        """撤单"""
        return self.context.order_api.cancel(order_id)

    # ===== 数据接口 =====

    def subscribe(self, symbol: str):
        """订阅行情"""
        self.context.data_api.subscribe(symbol)

    def get_bar(self, symbol: str, freq: str = "1m", count: int = 100):
        """获取历史K线"""
        return self.context.data_api.get_bar(symbol, freq, count)

    def get_position(self, symbol: str = None):
        """获取持仓"""
        return self.context.data_api.get_position(symbol)

    def get_account(self):
        """获取账户资金"""
        return self.context.data_api.get_account()
