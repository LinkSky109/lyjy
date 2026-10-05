"""
K线合成器 - 将tick数据合成为多周期K线
"""
from dataclasses import dataclass, field
from typing import Dict, List, Optional
from datetime import datetime, timedelta


@dataclass
class Bar:
    """K线数据"""
    symbol: str
    frequency: str
    open: float
    high: float
    low: float
    close: float
    volume: float = 0
    turnover: float = 0
    open_interest: float = 0
    bar_time: datetime = None

    def to_dict(self) -> dict:
        return {
            "symbol": self.symbol,
            "frequency": self.frequency,
            "open": self.open,
            "high": self.high,
            "low": self.low,
            "close": self.close,
            "volume": self.volume,
            "turnover": self.turnover,
            "open_interest": self.open_interest,
            "bar_time": self.bar_time.isoformat() if self.bar_time else None,
        }


class BarGenerator:
    """K线合成器

    将tick数据合成为指定周期的K线，支持：
    1m/5m/15m/30m/1h/4h/1d
    """

    PERIOD_SECONDS = {
        "1m": 60,
        "5m": 300,
        "15m": 900,
        "30m": 1800,
        "1h": 3600,
        "4h": 14400,
        "1d": 86400,
    }

    def __init__(self, frequency: str = "1m"):
        self.frequency = frequency
        self.period = self.PERIOD_SECONDS.get(frequency, 60)
        self._current_bars: Dict[str, Bar] = {}
        self._completed_bars: List[Bar] = []

    def update_tick(self, symbol: str, price: float, volume: float,
                    turnover: float, open_interest: float,
                    tick_time: datetime) -> Optional[Bar]:
        """更新一个tick

        Returns:
            如果K线完成则返回完成的Bar，否则返回None
        """
        bar_start = self._get_bar_start(tick_time)

        current = self._current_bars.get(symbol)

        if current is None or current.bar_time != bar_start:
            # 新K线开始，保存旧K线
            if current is not None:
                self._completed_bars.append(current)

            current = Bar(
                symbol=symbol,
                frequency=self.frequency,
                open=price,
                high=price,
                low=price,
                close=price,
                volume=volume,
                turnover=turnover,
                open_interest=open_interest,
                bar_time=bar_start,
            )
            self._current_bars[symbol] = current
            return None

        # 更新当前K线
        current.high = max(current.high, price)
        current.low = min(current.low, price)
        current.close = price
        current.volume += volume
        current.turnover += turnover
        current.open_interest = open_interest

        return None

    def _get_bar_start(self, tick_time: datetime) -> datetime:
        """计算tick所属K线的开始时间"""
        if self.frequency == "1d":
            return tick_time.replace(hour=0, minute=0, second=0, microsecond=0)

        timestamp = int(tick_time.timestamp())
        bar_start_ts = timestamp - (timestamp % self.period)
        return datetime.fromtimestamp(bar_start_ts)

    def get_current_bar(self, symbol: str) -> Optional[Bar]:
        return self._current_bars.get(symbol)

    def get_completed_bars(self) -> List[Bar]:
        return self._completed_bars

    def force_complete(self) -> List[Bar]:
        """强制完成所有当前K线（回测结束时调用）"""
        for bar in self._current_bars.values():
            self._completed_bars.append(bar)
        self._current_bars.clear()
        return self._completed_bars
