"""真实行情 + 自写策略回测演示（OBS-008）。

流程：
1) 从平台拉真实 K 线（/api/market/bars，数据来自 Gate.io 已入库）；
2) 用自写策略（此处复用示例双均线）在每根真实 bar 触发 on_bar 信号；
3) SimpleMatchingEngine 按收盘价撮合记账；
4) 输出净值/成交/收益率。

运行（后端已 ./scripts/dev.sh up 且真实行情已入库）：
    cd strategy-sdk && python lhjypt/examples/real_backtest_demo.py
"""
import sys

from lhjypt.backtest.backtest_api import BacktestDataAPI, BacktestOrderAPI
from lhjypt.backtest.real_data import load_real_bars
from lhjypt.backtest.simple_matching import SimpleMatchingEngine
from lhjypt.live.live_api import RestClient
from lhjypt.runtime.runner import StrategyRunner
from lhjypt.examples.dual_ma_strategy import DualMaStrategy


def main():
    symbol = sys.argv[1] if len(sys.argv) > 1 else "BTC_USDT"
    frequency = sys.argv[2] if len(sys.argv) > 2 else "1h"
    days = int(sys.argv[3]) if len(sys.argv) > 3 else 7
    initial_cash = 1_000_000.0

    client = RestClient()
    end = "2026-10-08T23:59:59"
    start = "2026-10-%02dT00:00:00" % max(1, 8 - days)

    print("拉取真实行情: %s@%s 窗口 %s ~ %s" % (symbol, frequency, start, end))
    bars = load_real_bars(client, symbol, frequency, start_time=start, end_time=end)

    matching = SimpleMatchingEngine(initial_cash=initial_cash)
    data_api = BacktestDataAPI()
    data_api.set_history_data(symbol, bars)
    order_api = BacktestOrderAPI(matching_engine=matching)
    runner = StrategyRunner(data_api=data_api, order_api=order_api)

    runner.create_strategy(DualMaStrategy, strategy_id="real-", strategy_name="真实双均线")
    runner.init()
    runner.start()
    for bar in bars:
        matching.set_price(symbol, bar.close)
        runner.on_bar(bar.to_dict())
    runner.stop()

    print("\n=== 真实行情回测结果（%d 根 K 线）===" % len(bars))
    summary = matching.summary()
    for key, value in summary.items():
        print("  %-12s %s" % (key, value))
    print("  最近成交:")
    for trade in matching.trades[-3:]:
        print("    %s %s x%s @ %s fee=%s" % (
            trade["symbol"], trade["side"], trade["volume"], trade["price"], trade["fee"]))


if __name__ == "__main__":
    main()