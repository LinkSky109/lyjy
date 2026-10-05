import time

from lhjypt import Bar, Context, Strategy
from lhjypt.backtest.backtest_api import BacktestDataAPI, BacktestOrderAPI
from lhjypt.engine.event import EventEngine
from lhjypt.examples.dual_ma_strategy import DualMaStrategy
from lhjypt.examples.grid_strategy import GridStrategy
from lhjypt.indicators.moving_average import WMA
from lhjypt.indicators.volume import OBV, VR


class FakeDataAPI:
    def __init__(self):
        self.subscriptions = []

    def subscribe(self, symbol):
        self.subscriptions.append(symbol)

    def get_bar(self, symbol, freq="1m", count=100):
        return [{"symbol": symbol, "close": 1.0}]

    def get_position(self, symbol=None):
        return []

    def get_account(self):
        return {"availableCash": 1000}


class FakeOrderAPI:
    def __init__(self):
        self.orders = []

    def buy(self, symbol, volume, price=None):
        self.orders.append(("BUY", symbol, volume, price))
        return "OID"

    def sell(self, symbol, volume, price=None):
        self.orders.append(("SELL", symbol, volume, price))
        return "OID"

    def short(self, symbol, volume, price=None):
        self.orders.append(("SHORT", symbol, volume, price))
        return "OID"

    def cover(self, symbol, volume, price=None):
        self.orders.append(("COVER", symbol, volume, price))
        return "OID"

    def cancel(self, order_id):
        self.orders.append(("CANCEL", order_id))
        return True


def strategy_context():
    return Context(
        strategy_id="S-001",
        strategy_name="coverage",
        data_api=FakeDataAPI(),
        order_api=FakeOrderAPI(),
    )


def test_strategy_wrapper_methods():
    class DummyStrategy(Strategy):
        def on_init(self):
            pass

    strategy = DummyStrategy()
    context = strategy_context()
    strategy.set_context(context)

    strategy.buy("rb2410", 1, 3500)
    strategy.sell("rb2410", 1, 3500)
    strategy.short("rb2410", 1, 3500)
    strategy.cover("rb2410", 1, 3500)
    strategy.cancel_order("OID")
    strategy.subscribe("rb2410")
    strategy.get_bar("rb2410")
    strategy.get_position("rb2410")
    strategy.get_account()

    assert context.data_api.subscriptions == ["rb2410"]
    assert len(context.order_api.orders) == 5


def test_dual_ma_and_grid_examples_run():
    dual = DualMaStrategy()
    dual.fast_period = 2
    dual.slow_period = 3
    dual.set_context(strategy_context())
    dual.on_init()
    for close in (1, 2, 3, 4, 3, 2):
        dual.on_bar({"symbol": "rb2410", "close": close})

    grid = GridStrategy()
    grid.grid_num = 2
    grid.lower_price = 100
    grid.upper_price = 120
    grid.set_context(strategy_context())
    grid.on_init()
    grid.on_bar({"symbol": "rb2410", "close": 100})
    grid.on_bar({"symbol": "rb2410", "close": 120})

    assert len(dual.context.order_api.orders) >= 2
    assert len(grid.context.order_api.orders) >= 2


class FakeMatchingEngine:
    def __init__(self):
        self.calls = []

    def submitBuyLimit(self, symbol, price, volume):
        self.calls.append(("BUY_LIMIT", symbol, price, volume))

    def submitBuyMarket(self, symbol, volume):
        self.calls.append(("BUY_MARKET", symbol, volume))

    def submitSellLimit(self, symbol, price, volume):
        self.calls.append(("SELL_LIMIT", symbol, price, volume))

    def submitSellMarket(self, symbol, volume):
        self.calls.append(("SELL_MARKET", symbol, volume))

    def cancelOrder(self, order_id):
        return True


def test_backtest_apis_cover_all_order_paths():
    data_api = BacktestDataAPI()
    bars = [
        Bar("rb2410", "1m", 1, 2, 0.5, 1.5),
        Bar("rb2410", "1m", 1.5, 2.5, 1.0, 2.0),
    ]
    data_api.set_history_data("rb2410", bars)
    data_api.set_account({"availableCash": 5000})
    data_api.set_position("rb2410", 2)

    assert len(data_api.get_bar("rb2410", count=1)) == 1
    assert data_api.get_tick("rb2410")["close"] == 2.0
    assert data_api.get_position("rb2410")[0]["quantity"] == 2
    assert data_api.get_account()["availableCash"] == 5000

    engine = FakeMatchingEngine()
    order_api = BacktestOrderAPI(engine)
    order_api.buy("rb2410", 1, 3500)
    order_api.buy("rb2410", 1)
    order_api.sell("rb2410", 1, 3500)
    order_api.sell("rb2410", 1)
    order_api.short("rb2410", 1, 3500)
    order_api.cover("rb2410", 1, 3500)
    assert order_api.cancel_all("rb2410") == 6


def test_indicator_edge_cases_and_event_engine():
    assert OBV([], []) == []
    assert OBV([1, 2, 1], [10, 20, 30])[-1] == -10
    assert VR([1, 2, 3], [1, 1, 1], period=2)[-1] == 100.0
    assert WMA([1, 2, 3], 3)[-1] == 14 / 6

    engine = EventEngine()
    handled = []

    def handler(data):
        handled.append(data)

    engine.register("tick", handler)
    engine.unregister("tick", handler)
    engine.start()
    engine.put("tick", 1)
    time.sleep(0.05)
    engine.stop()
    assert handled == []
