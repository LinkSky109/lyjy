"""
实盘数据API与订单API
通过REST API与Java核心服务对接
"""
import json
import urllib.request
import urllib.parse
from typing import List, Optional
from ..api.data import DataAPI
from ..api.order import OrderAPI


class RestClient:
    """HTTP REST客户端"""

    def __init__(self, base_url: str = "http://localhost:8081"):
        self.base_url = base_url.rstrip("/")

    def get(self, path: str, params: dict = None) -> dict:
        url = self.base_url + path
        if params:
            url += "?" + urllib.parse.urlencode(params)
        req = urllib.request.Request(url, method="GET")
        with urllib.request.urlopen(req) as resp:
            return json.loads(resp.read().decode())

    def post(self, path: str, params: dict = None, body=None) -> dict:
        url = self.base_url + path
        if params:
            url += "?" + urllib.parse.urlencode(params)
        data = json.dumps(body).encode() if body is not None else None
        req = urllib.request.Request(url, data=data, method="POST",
                                     headers={"Content-Type": "application/json"})
        with urllib.request.urlopen(req) as resp:
            return json.loads(resp.read().decode())


class LiveDataAPI(DataAPI):
    """实盘数据API"""

    def __init__(self, client: RestClient):
        self.client = client

    def subscribe(self, symbol: str):
        self.client.post("/api/market/subscribe", params={"symbol": symbol})

    def get_bar(self, symbol: str, freq: str = "1m", count: int = 100) -> List[dict]:
        result = self.client.get("/api/market/bars", params={
            "symbol": symbol, "frequency": freq,
            "startTime": "2020-01-01T00:00:00",
            "endTime": "2030-01-01T00:00:00", "limit": count
        })
        return result.get("data", [])

    def get_tick(self, symbol: str) -> Optional[dict]:
        result = self.client.get(f"/api/market/tick/{symbol}")
        return result.get("data")

    def get_position(self, symbol: str = None) -> List[dict]:
        # 实际对接持仓查询接口
        return []

    def get_account(self) -> dict:
        return {}


class LiveOrderAPI(OrderAPI):
    """实盘订单API"""

    def __init__(self, client: RestClient):
        self.client = client

    def buy(self, symbol: str, volume: float, price: float = None) -> str:
        return self._place_order(symbol, "BUY", volume, price)

    def sell(self, symbol: str, volume: float, price: float = None) -> str:
        return self._place_order(symbol, "SELL", volume, price)

    def short(self, symbol: str, volume: float, price: float = None) -> str:
        return self._place_order(symbol, "SHORT", volume, price)

    def cover(self, symbol: str, volume: float, price: float = None) -> str:
        return self._place_order(symbol, "COVER", volume, price)

    def cancel(self, order_id: str) -> bool:
        return True

    def cancel_all(self, symbol: str = None) -> int:
        return 0

    def _place_order(self, symbol: str, direction: str,
                     volume: float, price: float) -> str:
        # 调用交易下单接口
        return f"LIVE-ORD-{symbol}-{direction}"
