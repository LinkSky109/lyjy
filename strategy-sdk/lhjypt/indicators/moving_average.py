"""
移动平均线指标
- SMA: 简单移动平均
- EMA: 指数移动平均
- WMA: 加权移动平均
"""
from typing import List, Optional


def SMA(values: List[float], period: int) -> List[Optional[float]]:
    """简单移动平均

    Args:
        values: 价格序列
        period: 周期
    Returns:
        与输入等长的序列，前period-1个为None
    """
    result = [None] * len(values)
    if len(values) < period:
        return result

    window_sum = sum(values[:period])
    result[period - 1] = window_sum / period

    for i in range(period, len(values)):
        window_sum += values[i] - values[i - period]
        result[i] = window_sum / period

    return result


def EMA(values: List[float], period: int) -> List[Optional[float]]:
    """指数移动平均

    EMA(t) = price(t) * k + EMA(t-1) * (1-k), k = 2/(period+1)
    """
    result = [None] * len(values)
    if len(values) < period:
        return result

    k = 2 / (period + 1)
    # 用前period个值的SMA作为初始值
    ema = sum(values[:period]) / period
    result[period - 1] = ema

    for i in range(period, len(values)):
        ema = values[i] * k + ema * (1 - k)
        result[i] = ema

    return result


def WMA(values: List[float], period: int) -> List[Optional[float]]:
    """加权移动平均，近期数据权重更高"""
    result = [None] * len(values)
    if len(values) < period:
        return result

    weights = list(range(1, period + 1))
    weight_sum = sum(weights)

    for i in range(period - 1, len(values)):
        window = values[i - period + 1:i + 1]
        result[i] = sum(w * p for w, p in zip(weights, window)) / weight_sum

    return result
