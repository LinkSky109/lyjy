"""
技术指标单元测试
"""
import pytest
from lhjypt.indicators import SMA, EMA, RSI, BOLL, MACD, KDJ, ATR, OBV


class TestMovingAverage:
    def test_sma(self):
        values = [1, 2, 3, 4, 5]
        result = SMA(values, 3)
        assert result[:2] == [None, None]
        assert result[2] == pytest.approx(2.0)
        assert result[4] == pytest.approx(4.0)

    def test_ema(self):
        values = list(range(1, 21))
        result = EMA(values, 5)
        assert result[4] is not None
        assert result[-1] is not None
        # EMA应跟随上升趋势
        assert result[-1] > result[4]


class TestMomentum:
    def test_rsi_range(self):
        values = [float(i % 10) for i in range(30)]
        result = RSI(values, 14)
        for v in result:
            if v is not None:
                assert 0 <= v <= 100

    def test_macd_components(self):
        values = [10 + i * 0.5 for i in range(50)]
        dif, dea, hist = MACD(values)
        assert len(dif) == len(dea) == len(hist) == 50

    def test_kdj(self):
        highs = [i + 2 for i in range(30)]
        lows = [i - 2 for i in range(30)]
        closes = [float(i) for i in range(30)]
        k, d, j = KDJ(highs, lows, closes)
        assert len(k) == len(d) == len(j) == 30


class TestVolatility:
    def test_boll(self):
        values = [float(i % 20) for i in range(40)]
        mid, upper, lower = BOLL(values, 20)
        for i in range(19, 40):
            assert upper[i] > mid[i] > lower[i]

    def test_atr_positive(self):
        highs = [i + 1 for i in range(30)]
        lows = [i - 1 for i in range(30)]
        closes = [float(i) for i in range(30)]
        result = ATR(highs, lows, closes, 14)
        for v in result:
            if v is not None:
                assert v > 0


class TestVolume:
    def test_obv(self):
        closes = [1, 2, 1, 3, 2]
        volumes = [10, 20, 15, 30, 25]
        result = OBV(closes, volumes)
        assert result[0] == 0
        assert result[1] == 20
        assert result[2] == 5
