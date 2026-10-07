"""SimpleMatchingEngine 单测：bar 级收盘撮合与记账。"""
from lhjypt.backtest.simple_matching import SimpleMatchingEngine


def test_buy_then_sell_records_cash_and_position():
    engine = SimpleMatchingEngine(initial_cash=1000000)
    engine.set_price("BTC_USDT", 84000.0)
    engine.submitBuyMarket("BTC_USDT", 1.0)   # 买入 1 @ 84000
    assert engine.position("BTC_USDT") == 1.0
    assert engine.cash < 1000000 - 84000       # 现金扣：成交 + 手续费
    fee = 84000 * 0.0003
    assert abs(engine.cash - (1000000 - 84000 - fee)) < 1e-6

    engine.set_price("BTC_USDT", 85000.0)
    engine.submitSellMarket("BTC_USDT", 1.0)  # 卖出 1 @ 85000
    assert engine.position("BTC_USDT") == 0.0
    sell_fee = 85000 * 0.0003
    assert abs(engine.cash - (1000000 - 84000 - fee + 85000 - sell_fee)) < 1e-6
    assert engine.trade_count() == 2
    equity = engine.equity()
    assert equity > 1000000 - (84000 + 85000) * 2 * 0.0003  # 含价差收益


def test_limit_orders_fill_only_at_reference_price():
    engine = SimpleMatchingEngine()
    engine.set_price("BTC_USDT", 84000.0)
    assert engine.submitBuyLimit("BTC_USDT", 83950.0, 1.0) is False  # 高于盘面不成交
    assert engine.position("BTC_USDT") == 0.0
    assert engine.submitBuyLimit("BTC_USDT", 84000.0, 1.0) is True   # 等于盘面成交
    assert engine.position("BTC_USDT") == 1.0