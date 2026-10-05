"""
成交量指标
- OBV: 能量潮
- VR: 容量比率
"""
from typing import List, Optional


def OBV(closes: List[float], volumes: List[float]) -> List[Optional[float]]:
    """能量潮 OBV

    上涨日加成交量，下跌日减成交量
    """
    result = [None] * len(closes)
    if not closes:
        return result

    obv = 0
    result[0] = obv
    for i in range(1, len(closes)):
        if closes[i] > closes[i - 1]:
            obv += volumes[i]
        elif closes[i] < closes[i - 1]:
            obv -= volumes[i]
        result[i] = obv

    return result


def VR(closes: List[float], volumes: List[float], period: int = 26
       ) -> List[Optional[float]]:
    """容量比率

    VR = (上涨日成交量总和 + 0.5*平盘日成交量) / (下跌日成交量总和 + 0.5*平盘日成交量) * 100
    """
    result = [None] * len(closes)
    if len(closes) < period + 1:
        return result

    for i in range(period, len(closes)):
        up_vol = 0
        down_vol = 0
        flat_vol = 0
        for j in range(i - period + 1, i + 1):
            if closes[j] > closes[j - 1]:
                up_vol += volumes[j]
            elif closes[j] < closes[j - 1]:
                down_vol += volumes[j]
            else:
                flat_vol += volumes[j]

        denominator = down_vol + 0.5 * flat_vol
        if denominator == 0:
            result[i] = 100.0
        else:
            result[i] = (up_vol + 0.5 * flat_vol) / denominator * 100

    return result
