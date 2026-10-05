"""
订单API - 交易下单/撤单
"""
from abc import ABC, abstractmethod
from typing import Optional


class OrderAPI(ABC):
    """交易下单接口抽象"""

    @abstractmethod
    def buy(self, symbol: str, volume: float, price: float = None) -> str:
        """买入开仓，返回订单号"""
        pass

    @abstractmethod
    def sell(self, symbol: str, volume: float, price: float = None) -> str:
        """卖出平仓"""
        pass

    @abstractmethod
    def short(self, symbol: str, volume: float, price: float = None) -> str:
        """卖出开仓（期货）"""
        pass

    @abstractmethod
    def cover(self, symbol: str, volume: float, price: float = None) -> str:
        """买入平仓（期货）"""
        pass

    @abstractmethod
    def cancel(self, order_id: str) -> bool:
        """撤单"""
        pass

    @abstractmethod
    def cancel_all(self, symbol: str = None) -> int:
        """全部撤单，返回撤单数量"""
        pass
