"""SDK 回测用轻量撮合引擎（bar 级，收盘价撮合）。

供 BacktestOrderAPI 接入：策略在 on_bar 内下单时，引擎按当前 bar 收盘价
（或限价条件）撮合并记账（现金/持仓/手续费/已实现盈亏），足够支撑策略
信号的端到端回测验证。真实行情数据驱动下即为"真实行情 + 自写策略 + 本地撮合"。
"""
from typing import Dict, List


class SimpleMatchingEngine:
    """按当前 bar 收盘价撮合的最小撮合器。"""

    def __init__(self, initial_cash: float = 1_000_000.0,
                 commission_rate: float = 0.0003):
        self.initial_cash = float(initial_cash)
        self.cash = self.initial_cash
        self.positions: Dict[str, float] = {}
        self.commission_rate = commission_rate
        self.current_price: Dict[str, float] = {}
        self.trades: List[dict] = []
        self._order_seq = 0

    # ---- 行情上下文 ----
    def set_price(self, symbol: str, price: float):
        """设置当前 bar 收盘价（每次 on_bar 前调用）。"""
        self.current_price[symbol] = float(price)

    # ---- 撮合接口（BacktestOrderAPI 调用）----
    def submitBuyMarket(self, symbol: str, volume: float):
        self._fill(symbol, "BUY", float(volume), self.current_price.get(symbol, 0.0))

    def submitSellMarket(self, symbol: str, volume: float):
        self._fill(symbol, "SELL", float(volume), self.current_price.get(symbol, 0.0))

    def submitBuyLimit(self, symbol: str, price: float, volume: float) -> bool:
        current = self.current_price.get(symbol, 0.0)
        if current <= float(price):
            self._fill(symbol, "BUY", float(volume), current)
            return True
        return False

    def submitSellLimit(self, symbol: str, price: float, volume: float) -> bool:
        current = self.current_price.get(symbol, 0.0)
        if current >= float(price):
            self._fill(symbol, "SELL", float(volume), current)
            return True
        return False

    # ---- 记账 ----
    def _fill(self, symbol: str, side: str, volume: float, price: float):
        if price <= 0 or volume <= 0:
            return
        turnover = price * volume
        fee = turnover * self.commission_rate
        if side == "BUY":
            self.cash -= turnover + fee
            self.positions[symbol] = self.positions.get(symbol, 0.0) + volume
        else:
            self.cash += turnover - fee
            self.positions[symbol] = self.positions.get(symbol, 0.0) - volume
        self._order_seq += 1
        self.trades.append({
            "seq": self._order_seq,
            "symbol": symbol,
            "side": side,
            "volume": round(volume, 6),
            "price": round(price, 6),
            "fee": round(fee, 6),
        })

    def position(self, symbol: str) -> float:
        return self.positions.get(symbol, 0.0)

    def equity(self) -> float:
        """现金 + 持仓按当前价估值。"""
        total = self.cash
        for symbol, qty in self.positions.items():
            total += qty * self.current_price.get(symbol, 0.0)
        return total

    def trade_count(self) -> int:
        return len(self.trades)

    def summary(self) -> dict:
        return {
            "cash": round(self.cash, 2),
            "positions": self.positions,
            "equity": round(self.equity(), 2),
            "trades": self.trade_count(),
            "return": round((self.equity() / self.initial_cash - 1) * 100, 4),
        }