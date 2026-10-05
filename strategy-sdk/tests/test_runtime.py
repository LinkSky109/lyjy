"""
K线合成器与策略运行器测试
"""
from datetime import datetime, timedelta
from lhjypt.engine.bar_generator import BarGenerator
from lhjypt.backtest import BacktestDataAPI, BacktestOrderAPI
from lhjypt.runtime import StrategyRunner
from lhjypt import Strategy


class TestBarGenerator:
    def test_1m_generation(self):
        gen = BarGenerator("1m")
        base = datetime(2026, 10, 2, 10, 0, 0)

        for i in range(60):
            gen.update_tick("rb2410", 3500 + i * 0.1, 10, 35000, 1000,
                            base + timedelta(seconds=i))

        bars = gen.force_complete()
        assert len(bars) >= 1
        assert bars[0].open < bars[0].close

    def test_5m_generation(self):
        gen = BarGenerator("5m")
        base = datetime(2026, 10, 2, 10, 0, 0)

        for i in range(300):
            gen.update_tick("rb2410", 3500 + (i % 10), 1, 3500, 1000,
                            base + timedelta(seconds=i))

        bars = gen.force_complete()
        assert len(bars) >= 1


class TestBacktestAPI:
    def test_data_api(self):
        api = BacktestDataAPI()
        api.subscribe("rb2410")
        assert "rb2410" in api._subscribed

    def test_order_api(self):
        api = BacktestOrderAPI()
        order_id = api.buy("rb2410", 1, 3500)
        assert order_id.startswith("BT-ORD-")
        assert len(api._orders) == 1


class TestStrategyRunner:
    def test_create_and_init(self):
        class TestStrategy(Strategy):
            def on_init(self):
                self.initialized = True

        runner = StrategyRunner()
        runner.create_strategy(TestStrategy, "test-001", "测试策略")
        runner.init()
        assert runner.strategy.initialized is True
        assert runner.context.inited is True

    def test_start_stop(self):
        class TestStrategy(Strategy):
            def on_init(self): pass
            def on_start(self): self.started = True
            def on_stop(self): self.stopped = True

        runner = StrategyRunner()
        runner.create_strategy(TestStrategy)
        runner.start()
        assert runner.strategy.started is True
        assert runner.context.trading is True
        runner.stop()
        assert runner.strategy.stopped is True
