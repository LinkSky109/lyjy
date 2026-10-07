"""真实行情数据入口单测：平台 K 线 JSON → Bar 序列。"""
from lhjypt.backtest.real_data import load_real_bars


class StubClient:
    """模拟 RestClient.get 返回平台 /api/market/bars 响应。"""

    def __init__(self, rows):
        self._rows = rows
        self.params = None

    def get(self, path, params=None):
        self.params = params
        return {"code": 0, "data": self._rows}


def test_load_real_bars_parses_and_sorts_ascending():
    client = StubClient([
        {"symbol": "BTC_USDT", "frequency": "1h", "open": 84100.0, "high": 84500.0,
         "low": 83800.0, "close": 84390.0, "volume": 1484.0, "turnover": 1.2e8,
         "barTime": "2026-10-07T13:00:00"},
        {"symbol": "BTC_USDT", "frequency": "1h", "open": 84000.0, "high": 84200.0,
         "low": 83600.0, "close": 84155.0, "volume": 98.0, "turnover": 8.24e6,
         "barTime": "2026-10-07T14:00:00"},
    ])
    bars = load_real_bars(client, "BTC_USDT", "1h")
    assert client.params["symbol"] == "BTC_USDT"
    assert bars[0].bar_time < bars[1].bar_time          # 升序
    assert bars[0].close == 84390.0
    assert bars[1].close == 84155.0
    assert bars[1].volume == 98.0


def test_load_real_bars_raises_when_empty():
    try:
        load_real_bars(StubClient([]), "ETH_USDT", "1h")
        raise AssertionError("should have raised")
    except ValueError as error:
        assert "无真实行情" in str(error)
        assert "refresh" in str(error)