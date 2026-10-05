"""
策略运行器 - 策略加载、初始化、运行框架
支持回测模式和实盘模式
"""
import importlib.util
import logging
from typing import Optional
from ..api.context import Context
from ..api.strategy import Strategy
from ..backtest.backtest_api import BacktestDataAPI, BacktestOrderAPI
from ..engine.event import EventEngine

logger = logging.getLogger("lhjypt.runtime")


class StrategyRunner:
    """策略运行器

    负责：
    1. 加载策略类
    2. 创建运行上下文
    3. 初始化策略
    4. 驱动行情事件
    5. 管理策略生命周期
    """

    def __init__(self, data_api: BacktestDataAPI = None,
                 order_api: BacktestOrderAPI = None):
        self.data_api = data_api or BacktestDataAPI()
        self.order_api = order_api or BacktestOrderAPI()
        self.event_engine = EventEngine()
        self.strategy: Optional[Strategy] = None
        self.context: Optional[Context] = None

    @staticmethod
    def load_strategy_from_file(file_path: str, class_name: str = None) -> type:
        """从Python文件加载策略类

        Args:
            file_path: 策略文件路径
            class_name: 策略类名，不传则自动查找Strategy子类
        """
        spec = importlib.util.spec_from_file_location("user_strategy", file_path)
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)

        if class_name:
            return getattr(module, class_name)

        # 自动查找Strategy子类
        for attr in dir(module):
            obj = getattr(module, attr)
            if isinstance(obj, type) and issubclass(obj, Strategy) and obj is not Strategy:
                return obj

        raise ValueError(f"未在 {file_path} 中找到Strategy子类")

    def create_strategy(self, strategy_class: type, strategy_id: str = "",
                        strategy_name: str = "", parameters: dict = None) -> Strategy:
        """创建策略实例并注入上下文"""
        self.strategy = strategy_class()
        self.context = Context(
            strategy_id=strategy_id or strategy_class.__name__,
            strategy_name=strategy_name or strategy_class.__name__,
            data_api=self.data_api,
            order_api=self.order_api,
            parameters=parameters or {},
        )
        self.strategy.set_context(self.context)
        self.strategy.set_event_engine(self.event_engine)
        self.strategy.strategy_id = self.context.strategy_id
        self.strategy.strategy_name = self.context.strategy_name
        return self.strategy

    def init(self):
        """初始化策略"""
        if not self.strategy:
            raise RuntimeError("策略未创建")
        logger.info("初始化策略: %s", self.context.strategy_id)
        self.strategy.on_init()
        self.context.inited = True

    def start(self):
        """启动策略"""
        if not self.context.inited:
            self.init()
        self.event_engine.start()
        self.strategy.on_start()
        self.context.trading = True
        logger.info("策略已启动: %s", self.context.strategy_id)

    def stop(self):
        """停止策略"""
        self.strategy.on_stop()
        self.context.trading = False
        self.event_engine.stop()
        logger.info("策略已停止: %s", self.context.strategy_id)

    def on_bar(self, bar: dict):
        """推送K线给策略"""
        if self.context.trading:
            self.strategy.on_bar(bar)

    def on_tick(self, tick: dict):
        """推送tick给策略"""
        if self.context.trading:
            self.strategy.on_tick(tick)

    def on_order(self, order: dict):
        """推送订单状态变更"""
        self.strategy.on_order(order)

    def on_trade(self, trade: dict):
        """推送成交回报"""
        self.strategy.on_trade(trade)
