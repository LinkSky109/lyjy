"""
实盘数据 API 与订单 API。

通过 REST API 与 Java 核心服务对接，所有写操作携带用户、角色和幂等键。
"""
import json
import urllib.error
import urllib.parse
import urllib.request
import uuid
from typing import Dict, List, Optional

from ..api.data import DataAPI
from ..api.order import OrderAPI


class LiveApiError(RuntimeError):
    """核心服务返回的业务错误。"""

    def __init__(self, message: str, code: int = 500):
        super().__init__(message)
        self.code = code


class RestClient:
    """HTTP REST 客户端。"""

    def __init__(self, base_url: str = "http://localhost:8081",
                 headers: Optional[Dict[str, str]] = None,
                 timeout: float = 10.0):
        self.base_url = base_url.rstrip("/")
        self.default_headers = dict(headers or {})
        self.timeout = timeout

    def get(self, path: str, params: dict = None,
            headers: dict = None) -> dict:
        return self._request("GET", path, params=params, headers=headers)

    def post(self, path: str, params: dict = None, body=None,
             headers: dict = None) -> dict:
        return self._request("POST", path, params=params, body=body, headers=headers)

    def put(self, path: str, params: dict = None, body=None,
            headers: dict = None) -> dict:
        return self._request("PUT", path, params=params, body=body, headers=headers)

    def delete(self, path: str, params: dict = None,
               headers: dict = None) -> dict:
        return self._request("DELETE", path, params=params, headers=headers)

    def _request(self, method: str, path: str, params: dict = None,
                 body=None, headers: dict = None) -> dict:
        url = self.base_url + path
        if params:
            url += "?" + urllib.parse.urlencode(params)

        merged_headers = {"Content-Type": "application/json"}
        merged_headers.update(self.default_headers)
        if headers:
            merged_headers.update(headers)

        data = json.dumps(body).encode("utf-8") if body is not None else None
        request = urllib.request.Request(
            url, data=data, method=method, headers=merged_headers)
        try:
            with urllib.request.urlopen(request, timeout=self.timeout) as response:
                payload = json.loads(response.read().decode("utf-8") or "{}")
        except urllib.error.HTTPError as error:
            payload = self._read_error_payload(error)
            raise LiveApiError(
                payload.get("message", str(error)), payload.get("code", error.code))
        except urllib.error.URLError as error:
            raise LiveApiError(f"核心服务不可用: {error.reason}", 503) from error

        if payload.get("code", 0) != 0:
            raise LiveApiError(
                payload.get("message", "未知错误"), payload.get("code", 500))
        return payload

    @staticmethod
    def _read_error_payload(error: urllib.error.HTTPError) -> dict:
        try:
            content = error.read().decode("utf-8")
            return json.loads(content or "{}")
        except (ValueError, UnicodeDecodeError):
            return {}


class LiveDataAPI(DataAPI):
    """实盘数据 API。"""

    def __init__(self, client: RestClient, account_id: str = None,
                 strategy_id: str = None, user_id: str = None,
                 role: str = "TRADER"):
        self.client = client
        self.account_id = account_id
        self.strategy_id = strategy_id
        self.headers = self._user_headers(user_id, role)

    def subscribe(self, symbol: str):
        self.client.post(
            "/api/market/subscribe", params={"symbol": symbol}, headers=self.headers)

    def get_bar(self, symbol: str, freq: str = "1m", count: int = 100) -> List[dict]:
        result = self.client.get("/api/market/bars", params={
            "symbol": symbol, "frequency": freq,
            "startTime": "2020-01-01T00:00:00",
            "endTime": "2030-01-01T00:00:00", "limit": count
        }, headers=self.headers)
        return result.get("data", [])

    def get_tick(self, symbol: str) -> Optional[dict]:
        result = self.client.get(
            f"/api/market/tick/{symbol}", headers=self.headers)
        return result.get("data")

    def get_position(self, symbol: str = None) -> List[dict]:
        result = self.client.get("/api/trade/platform/positions", params={
            "accountId": self.account_id,
            "strategyId": self.strategy_id,
        }, headers=self.headers)
        positions = result.get("data", [])
        if symbol:
            positions = [p for p in positions if p.get("symbol") == symbol]
        return positions

    def get_account(self) -> dict:
        result = self.client.get("/api/trade/platform/account", params={
            "accountId": self.account_id,
        }, headers=self.headers)
        return result.get("data", {})

    @staticmethod
    def _user_headers(user_id: str, role: str) -> dict:
        headers = {"X-User-Role": role or "TRADER"}
        if user_id:
            headers["X-User-Id"] = user_id
        return headers


class LiveOrderAPI(OrderAPI):
    """实盘订单 API。"""

    def __init__(self, client: RestClient, account_id: str = None,
                 strategy_id: str = None, user_id: str = None,
                 role: str = "TRADER"):
        self.client = client
        self.account_id = account_id
        self.strategy_id = strategy_id
        self.headers = LiveDataAPI._user_headers(user_id, role)
        self.idempotency_key: Optional[str] = None
        self.last_idempotency_key: Optional[str] = None

    def set_idempotency_key(self, key: str):
        """为下一次及后续重试设置固定幂等键。"""
        self.idempotency_key = key

    def buy(self, symbol: str, volume: float, price: float = None) -> str:
        return self._place_order(symbol, "BUY", "OPEN", volume, price)

    def sell(self, symbol: str, volume: float, price: float = None) -> str:
        return self._place_order(symbol, "SELL", "CLOSE", volume, price)

    def short(self, symbol: str, volume: float, price: float = None) -> str:
        return self._place_order(symbol, "SELL", "OPEN", volume, price)

    def cover(self, symbol: str, volume: float, price: float = None) -> str:
        return self._place_order(symbol, "BUY", "CLOSE", volume, price)

    def cancel(self, order_id: str) -> bool:
        result = self.client.delete(
            f"/api/trade/orders/{order_id}", params={"reason": "SDK撤单"},
            headers=self.headers)
        return result.get("data", {}).get("status") == "CANCELED"

    def cancel_all(self, symbol: str = None) -> int:
        raise NotImplementedError("核心服务暂未提供全量撤单接口")

    def _place_order(self, symbol: str, direction: str, offset_flag: str,
                     volume: float, price: float = None) -> str:
        key = self.idempotency_key or f"SDK-{uuid.uuid4().hex}"
        self.last_idempotency_key = key
        body = {
            "accountId": self.account_id,
            "strategyId": self.strategy_id,
            "symbol": symbol,
            "direction": direction,
            "offsetFlag": offset_flag,
            "orderType": "LIMIT" if price is not None else "MARKET",
            "price": price,
            "quantity": volume,
            "idempotencyKey": key,
        }
        result = self.client.post(
            "/api/trade/orders", body=body, headers=self.headers)
        return result.get("data", {}).get("orderId")
