"""真实交易通道演练示例策略（在平台沙箱内执行，Mock 网关自动成交）。

链路：账户基线 -> 平旧仓 -> 限价开仓(BTCUSDT) -> 查询订单状态 -> 撤已成交单(预期被拒)
      -> 错误账户下单(预期被拒) -> 持仓复检。

该源码由 scripts/channel-demo.sh 上传为 CH-DEMO 并在沙箱运行；
所有输出进入沙箱 stdout，由平台 outputTail 收集。
"""
from lhjypt.api.strategy import Strategy
from lhjypt.live.live_api import LiveApiError, LiveDataAPI, LiveOrderAPI, RestClient


class ChannelDemo(Strategy):

    def on_init(self):
        print("[channel] init")

    def on_start(self):
        client = RestClient()
        data = LiveDataAPI(client, account_id="DEFAULT-001",
                           strategy_id="CH-DEMO", user_id="trader-1")
        orders = LiveOrderAPI(client, account_id="DEFAULT-001",
                              strategy_id="CH-DEMO", user_id="trader-1")

        acct = data.get_account()
        print("[channel] baseline available:", acct.get("availableCash"))

        # 1) 先平掉旧仓（幂等：每轮演练净持仓保持 0 -> 1 手）
        existing = data.get_position("BTCUSDT")
        if existing:
            print("[channel] closing legacy position:", len(existing))
            orders.sell("BTCUSDT", 1, price=200000.0)

        # 2) 限价开仓
        order_id = orders.buy("BTCUSDT", 1, price=200000.0)
        print("[channel] buy placed:", order_id)
        detail = client.get("/api/trade/orders/" + order_id, headers=orders.headers)
        print("[channel] order status:", detail.get("data", {}).get("status"))

        # 3) 撤已成交单 -> 预期拒绝（交易通道异常回报路径）
        try:
            canceled = orders.cancel(order_id)
            if canceled:
                print("[channel] cancel unexpected success")
            else:
                print("[channel] cancel rejected (expected): order FILLED")
        except LiveApiError as error:
            print("[channel] cancel expected-error:", error.code, str(error)[:70])

        # 4) 错误账户下单 -> 预期被拒
        try:
            bad = LiveOrderAPI(client, account_id="NO-SUCH-ACCOUNT",
                               strategy_id="CH-DEMO", user_id="trader-1")
            bad.buy("BTCUSDT", 1, price=200000.0)
            print("[channel] bad-account unexpected success")
        except LiveApiError as error:
            print("[channel] bad-account rejected:", error.code)

        # 5) 持仓复检
        positions = data.get_position("BTCUSDT")
        total = sum(float(p.get("quantity") or p.get("volume") or 0) for p in positions)
        print("[channel] final BTCUSDT position qty:", total)

        print("[channel] channel demo complete")