"""
回测数据API与订单API实现
与回测引擎对接，提供策略在回测环境中的数据查询和交易接口
"""
from typing import List, Optional, Dict
from ..api.data import DataAPI
from ..api.order import OrderAPI
from ..engine.bar_generator import BarGenerator, Bar


class BacktestDataAPI(DataAPI):
    """回测数据API"""

    def __init__(self):
        self._subscribed = set()
        self._history_bars: Dict[str, List[Bar]] = {}
        self._positions: Dict[str, float] = {}
        self._account = {
            "totalAsset": 1000000,
            "availableCash": 1000000,
            "frozenCash": 0,
        }
        self._bar_generators: Dict[str, BarGenerator] = {}

    def set_history_data(self, symbol: str, bars: List[Bar]):
        """加载历史数据"""
        self._history_bars[symbol] = bars

    def set_account(self, account: dict):
        self._account = account

    def set_position(self, symbol: str, quantity: float):
        self._positions[symbol] = quantity

    def subscribe(self, symbol: str):
        self._subscribed.add(symbol)

    def get_bar(self, symbol: str, freq: str = "1m", count: int = 100) -> List[dict]:
        bars = self._history_bars.get(symbol, [])
        return [b.to_dict() for b in bars[-count:]]

    def get_tick(self, symbol: str) -> Optional[dict]:
        bars = self._history_bars.get(symbol, [])
        return bars[-1].to_dict() if bars else None

    def get_position(self, symbol: str = None) -> List[dict]:
        if symbol:
            qty = self._positions.get(symbol, 0)
            return [{"symbol": symbol, "quantity": qty}] if qty else []
        return [{"symbol": s, "quantity": q} for s, q in self._positions.items() if q]

    def get_account(self) -> dict:
        return self._account


class BacktestOrderAPI(OrderAPI):
    """回测订单API - 将订单提交给撮合引擎"""

    def __init__(self, matching_engine=None):
        self._matching_engine = matching_engine
        self._orders: List[dict] = []
        self._order_counter = 0

    def set_matching_engine(self, engine):
        self._matching_engine = engine

    def _generate_order_id(self) -> str:
        self._order_counter += 1
        return f"BT-ORD-{self._order_counter:06d}"

    def buy(self, symbol: str, volume: float, price: float = None) -> str:
        order_id = self._generate_order_id()
        if self._matching_engine:
            if price:
                self._matching_engine.submitBuyLimit(symbol, price, volume)
            else:
                self._matching_engine.submitBuyMarket(symbol, volume)
        self._orders.append({"orderId": order_id, "symbol": symbol,
                             "direction": "BUY", "price": price, "volume": volume})
        return order_id

    def sell(self, symbol: str, volume: float, price: float = None) -> str:
        order_id = self._generate_order_id()
        if self._matching_engine:
            if price:
                self._matching_engine.submitSellLimit(symbol, price, volume)
            else:
                self._matching_engine.submitSellMarket(symbol, volume)
        self._orders.append({"orderId": order_id, "symbol": symbol,
                             "direction": "SELL", "price": price, "volume": volume})
        return order_id

    def short(self, symbol: str, volume: float, price: float = None) -> str:
        order_id = self._generate_order_id()
        if self._matching_engine and price:
            self._matching_engine.submitSellLimit(symbol, price, volume)
        self._orders.append({"orderId": order_id, "symbol": symbol,
                             "direction": "SHORT", "price": price, "volume": volume})
        return order_id

    def cover(self, symbol: str, volume: float, price: float = None) -> str:
        order_id = self._generate_order_id()
        if self._matching_engine and price:
            self._matching_engine.submitBuyLimit(symbol, price, volume)
        self._orders.append({"orderId": order_id, "symbol": symbol,
                             "direction": "COVER", "price": price, "volume": volume})
        return order_id

    def cancel(self, order_id: str) -> bool:
        if self._matching_engine:
            return self._matching_engine.cancelOrder(order_id)
        return False

    def cancel_all(self, symbol: str = None) -> int:
        count = 0
        for order in self._orders:
            if symbol is None or order["symbol"] == symbol:
                if self.cancel(order["orderId"]):
                    count += 1
        return count
