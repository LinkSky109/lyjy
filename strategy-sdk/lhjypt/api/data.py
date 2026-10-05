"""
数据API - 行情/持仓/账户查询
"""
from abc import ABC, abstractmethod
from typing import List, Optional


class DataAPI(ABC):
    """数据查询接口抽象"""

    @abstractmethod
    def subscribe(self, symbol: str):
        """订阅实时行情"""
        pass

    @abstractmethod
    def get_bar(self, symbol: str, freq: str, count: int) -> List[dict]:
        """获取历史K线数据

        Args:
            symbol: 标的代码
            freq: 周期 1m/5m/15m/1h/1d
            count: 返回条数
        """
        pass

    @abstractmethod
    def get_tick(self, symbol: str) -> Optional[dict]:
        """获取最新tick"""
        pass

    @abstractmethod
    def get_position(self, symbol: str = None) -> List[dict]:
        """获取持仓"""
        pass

    @abstractmethod
    def get_account(self) -> dict:
        """获取账户资金信息"""
        pass
