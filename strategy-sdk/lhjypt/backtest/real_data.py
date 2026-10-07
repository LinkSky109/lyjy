"""真实行情数据入口：从平台拉取已入库的真实 K 线（/api/market/bars），
转换为 SDK 回测可用的 Bar 列表，供自写策略 + SimpleMatchingEngine 回测。
"""
from datetime import datetime
from typing import List, Optional

from ..engine.bar_generator import Bar as EngineBar


def load_real_bars(client, symbol: str, frequency: str = "1h",
                   start_time: Optional[str] = None,
                   end_time: Optional[str] = None,
                   limit: int = 500) -> List[EngineBar]:
    """从平台拉真实 K 线（需先 POST /api/market/real/refresh 入库）。

    client: lhjypt.live.live_api.RestClient（带默认身份头）。
    返回按时间升序的 Bar 列表。
    """
    params = {"symbol": symbol, "frequency": frequency, "limit": limit}
    if start_time:
        params["startTime"] = start_time
    if end_time:
        params["endTime"] = end_time
    payload = client.get("/api/market/bars", params=params)
    rows = payload.get("data") or []
    if not rows:
        raise ValueError(
            "平台无真实行情: %s@%s（请先 POST /api/market/real/refresh 或查时间窗）"
            % (symbol, frequency))

    bars: List[EngineBar] = []
    for row in rows:
        bar_time = row.get("barTime") or row.get("bar_time")
        bars.append(EngineBar(
            symbol=row.get("symbol", symbol),
            frequency=row.get("frequency", frequency),
            open=float(row.get("open") or 0),
            high=float(row.get("high") or 0),
            low=float(row.get("low") or 0),
            close=float(row.get("close") or 0),
            volume=float(row.get("volume") or 0),
            turnover=float(row.get("turnover") or 0),
            open_interest=float(row.get("openInterest") or 0),
            bar_time=_parse_time(bar_time),
        ))
    # 升序（平台返回按时间倒序）
    bars.sort(key=lambda b: b.bar_time or datetime.min)
    return bars


def _parse_time(value) -> Optional[datetime]:
    if not value:
        return None
    if isinstance(value, datetime):
        return value
    text = str(value).replace("Z", "+00:00")
    try:
        return datetime.fromisoformat(text[:19])
    except ValueError:
        return None