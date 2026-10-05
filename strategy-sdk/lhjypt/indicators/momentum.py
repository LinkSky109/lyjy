"""
动量指标
- RSI: 相对强弱指标
- KDJ: 随机指标
- MACD: 指数平滑异同移动平均线
"""
from typing import List, Optional, Tuple
from .moving_average import EMA


def RSI(closes: List[float], period: int = 14) -> List[Optional[float]]:
    """相对强弱指标 RSI

    RSI = 100 - 100/(1+RS), RS = 平均涨幅/平均跌幅
    """
    result = [None] * len(closes)
    if len(closes) < period + 1:
        return result

    gains = []
    losses = []
    for i in range(1, len(closes)):
        change = closes[i] - closes[i - 1]
        gains.append(max(change, 0))
        losses.append(max(-change, 0))

    avg_gain = sum(gains[:period]) / period
    avg_loss = sum(losses[:period]) / period

    if avg_loss == 0:
        result[period] = 100.0
    else:
        rs = avg_gain / avg_loss
        result[period] = 100 - 100 / (1 + rs)

    for i in range(period + 1, len(closes)):
        avg_gain = (avg_gain * (period - 1) + gains[i - 1]) / period
        avg_loss = (avg_loss * (period - 1) + losses[i - 1]) / period
        if avg_loss == 0:
            result[i] = 100.0
        else:
            rs = avg_gain / avg_loss
            result[i] = 100 - 100 / (1 + rs)

    return result


def KDJ(highs: List[float], lows: List[float], closes: List[float],
        n: int = 9, m1: int = 3, m2: int = 3) -> Tuple[List, List, List]:
    """KDJ随机指标

    Returns:
        (K, D, J) 三个序列
    """
    length = len(closes)
    rsv = [None] * length
    k = [None] * length
    d = [None] * length
    j = [None] * length

    for i in range(n - 1, length):
        hh = max(highs[i - n + 1:i + 1])
        ll = min(lows[i - n + 1:i + 1])
        if hh == ll:
            rsv[i] = 50.0
        else:
            rsv[i] = (closes[i] - ll) / (hh - ll) * 100

    prev_k = 50.0
    prev_d = 50.0
    for i in range(length):
        if rsv[i] is not None:
            prev_k = (m1 - 1) / m1 * prev_k + 1 / m1 * rsv[i]
            prev_d = (m2 - 1) / m2 * prev_d + 1 / m2 * prev_k
            k[i] = prev_k
            d[i] = prev_d
            j[i] = 3 * prev_k - 2 * prev_d

    return k, d, j


def MACD(closes: List[float], fast: int = 12, slow: int = 26,
         signal: int = 9) -> Tuple[List, List, List]:
    """MACD指标

    Returns:
        (DIF, DEA, MACD柱)
    """
    ema_fast = EMA(closes, fast)
    ema_slow = EMA(closes, slow)

    dif = [None] * len(closes)
    for i in range(len(closes)):
        if ema_fast[i] is not None and ema_slow[i] is not None:
            dif[i] = ema_fast[i] - ema_slow[i]

    # DEA = DIF的EMA
    valid_dif = [v for v in dif if v is not None]
    dea_valid = EMA(valid_dif, signal) if len(valid_dif) >= signal else [None] * len(valid_dif)

    dea = [None] * len(closes)
    start_idx = len(closes) - len(valid_dif)
    for i, v in enumerate(dea_valid):
        dea[start_idx + i] = v

    macd_hist = [None] * len(closes)
    for i in range(len(closes)):
        if dif[i] is not None and dea[i] is not None:
            macd_hist[i] = 2 * (dif[i] - dea[i])

    return dif, dea, macd_hist
