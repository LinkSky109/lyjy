from lhjypt.live.live_api import LiveApiError, LiveDataAPI, LiveOrderAPI


class FakeClient:
    def __init__(self):
        self.calls = []
        self.response = {}

    def get(self, path, params=None, headers=None):
        self.calls.append(("GET", path, params, headers))
        return self.response

    def post(self, path, params=None, body=None, headers=None):
        self.calls.append(("POST", path, body, headers))
        return self.response

    def delete(self, path, params=None, headers=None):
        self.calls.append(("DELETE", path, params, headers))
        return self.response


def test_live_data_api_reads_platform_positions_and_account():
    client = FakeClient()
    data_api = LiveDataAPI(
        client, account_id="DEFAULT-001", strategy_id="S-001",
        user_id="trader-001", role="TRADER")

    client.response = {
        "code": 0,
        "data": [{"symbol": "rb2410", "quantity": 2}],
    }
    positions = data_api.get_position("rb2410")
    assert positions == [{"symbol": "rb2410", "quantity": 2}]
    assert client.calls[-1][1] == "/api/trade/platform/positions"
    assert client.calls[-1][3]["X-User-Id"] == "trader-001"

    client.response = {"code": 0, "data": {"availableCash": 999997.9}}
    assert data_api.get_account()["availableCash"] == 999997.9
    assert client.calls[-1][1] == "/api/trade/platform/account"


def test_live_order_api_posts_unified_order_with_idempotency_key():
    client = FakeClient()
    order_api = LiveOrderAPI(
        client, account_id="DEFAULT-001", strategy_id="S-001",
        user_id="trader-001", role="TRADER")
    order_api.set_idempotency_key("idem-001")
    client.response = {"code": 0, "data": {"orderId": "ORD-001"}}

    order_id = order_api.buy("rb2410", 2, 3500)

    assert order_id == "ORD-001"
    method, path, body, headers = client.calls[-1]
    assert method == "POST"
    assert path == "/api/trade/orders"
    assert body["idempotencyKey"] == "idem-001"
    assert body["offsetFlag"] == "OPEN"
    assert headers["X-User-Role"] == "TRADER"


def test_live_order_api_cancel_uses_platform_order_id():
    client = FakeClient()
    order_api = LiveOrderAPI(client, account_id="DEFAULT-001")
    client.response = {"code": 0, "data": {"status": "CANCELED"}}

    assert order_api.cancel("ORD-001") is True
    assert client.calls[-1][1] == "/api/trade/orders/ORD-001"


def test_live_order_api_does_not_fake_cancel_all():
    order_api = LiveOrderAPI(FakeClient())
    try:
        order_api.cancel_all()
        assert False, "cancel_all should not return fake success"
    except NotImplementedError:
        pass


def test_live_api_error_can_be_raised_by_client():
    error = LiveApiError("风控拒绝", 422)
    assert error.code == 422
