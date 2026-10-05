"""
策略运行上下文
"""
from dataclasses import dataclass, field
from typing import Optional
from .data import DataAPI
from .order import OrderAPI


@dataclass
class Context:
    """策略运行时上下文

    封装策略运行所需的所有外部依赖，由平台注入
    """
    strategy_id: str
    strategy_name: str
    data_api: DataAPI
    order_api: OrderAPI
    parameters: dict = field(default_factory=dict)

    # 运行时状态
    trading_day: str = ""
    inited: bool = False
    trading: bool = False

    def log(self, msg: str):
        """输出策略日志"""
        print(f"[{self.strategy_id}] {msg}")
