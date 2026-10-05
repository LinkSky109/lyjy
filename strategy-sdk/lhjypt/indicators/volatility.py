"""
波动率指标
- BOLL: 布林带
- ATR: 真实波动幅度均值
"""
from typing import List, Optional, Tuple
from .moving_average import SMA


def BOLL(closes: List[float], period: int = 20, nbdev: float = 2.0
         ) -> Tuple[List, List, List]:
    """布林带

    Returns:
        (中轨MID, 上轨UPPER, 下轨LOWER)
    """
    mid = SMA(closes, period)
    upper = [None] * len(closes)
    lower = [None] * len(closes)

    for i in range(period - 1, len(closes)):
        window = closes[i - period + 1:i + 1]
        m = mid[i]
        variance = sum((x - m) ** 2 for x in window) / period
        std = variance ** 0.5
        upper[i] = m + nbdev * std
        lower[i] = m - nbdev * std

    return mid, upper, lower


def ATR(highs: List[float], lows: List[float], closes: List[float],
        period: int = 14) -> List[Optional[float]]:
    """真实波动幅度均值

    TR = max(high-low, |high-prevClose|, |low-prevClose|)
    """
    tr = [None] * len(closes)
    result = [None] * len(closes)

    tr[0] = highs[0] - lows[0]
    for i in range(1, len(closes)):
        tr[i] = max(
            highs[i] - lows[i],
            abs(highs[i] - closes[i - 1]),
            abs(lows[i] - closes[i - 1])
        )

    if len(closes) < period:
        return result

    atr = sum(tr[1:period + 1]) / period
    result[period] = atr

    for i in range(period + 1, len(closes)):
        atr = (atr * (period - 1) + tr[i]) / period
        result[i] = atr

    return result
