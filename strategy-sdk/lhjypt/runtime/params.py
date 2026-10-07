"""策略参数读取（OBS-013）。

平台在沙箱启动时注入环境变量 ``LHJYPT_STRATEGY_PARAMS``（JSON 字符串，
来源于策略 ``params_json``，可由「策略参数优化」落档）。策略通过
``load_params()`` / ``get_param()`` 读取，使优化参数直接驱动运行中的策略。

用法：
    from lhjypt.runtime.params import get_param
    fast = int(get_param("fast", 5))
    slow = int(get_param("slow", 20))
"""
import json
import os
from typing import Any, Dict

ENV_KEY = "LHJYPT_STRATEGY_PARAMS"


def load_params() -> Dict[str, Any]:
    """读取平台注入的策略参数（缺失或非法返回空字典，绝不抛出）。"""
    raw = os.environ.get(ENV_KEY)
    if not raw or not raw.strip():
        return {}
    try:
        data = json.loads(raw)
        return data if isinstance(data, dict) else {}
    except (ValueError, TypeError):
        return {}


def get_param(key: str, default: Any = None) -> Any:
    """读取单个参数，缺失返回默认值。"""
    return load_params().get(key, default)
