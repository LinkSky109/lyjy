"""RSI 均值回归策略 · 真实行情回测。

运行（后端已启动且真实行情已入库）：
    cd strategy-sdk
    python examples/rsi_backtest.py BTC_USDT 1h 7
"""
import sys

from lhjypt.backtest.backtest_api import BacktestDataAPI, BacktestOrderAPI
from lhjypt.backtest.real_data import load_real_bars
from lhjypt.backtest.simple_matching import SimpleMatchingEngine
from lhjypt.live.live_api import RestClient
from lhjypt.runtime.runner import StrategyRunner
from lhjypt.examples.rsi_reversion import RsiReversionStrategy


def main():
    symbol = sys.argv[1] if len(sys.argv) > 1 else "BTC_USDT"
    frequency = sys.argv[2] if len(sys.argv) > 2 else "1h"
    days = int(sys.argv[3]) if len(sys.argv) > 3 else 7
    initial_cash = 1_000_000.0

    end = "2026-10-10T23:59:59"
    start = "2026-10-%02dT00:00:00" % max(1, 10 - days)

    client = RestClient()
    bars = load_real_bars(client, symbol, frequency, start_time=start, end_time=end)
    if not bars:
        print("未取到行情，请先刷新真实行情：POST /api/market/real/refresh")
        return

    matching = SimpleMatchingEngine(initial_cash=initial_cash)
    data_api = BacktestDataAPI()
    data_api.set_history_data(symbol, bars)
    runner = StrategyRunner(data_api=data_api,
                            order_api=BacktestOrderAPI(matching_engine=matching))
    runner.create_strategy(RsiReversionStrategy, strategy_id="rsi-demo",
                           strategy_name="RSI 均值回归")
    runner.init()
    runner.start()
    for bar in bars:
        matching.set_price(symbol, bar.close)
        runner.on_bar(bar.to_dict())
    runner.stop()

    print("\n=== RSI 回测结果（%s %s，%d 根 K 线）===" % (symbol, frequency, len(bars)))
    for key, value in matching.summary().items():
        print("  %-14s %s" % (key, value))
    print("  最近成交:")
    for trade in matching.trades[-5:]:
        print("    %s %s x%s @ %s fee=%s" % (
            trade["symbol"], trade["side"], trade["volume"],
            trade["price"], trade["fee"]))


if __name__ == "__main__":
    main()
