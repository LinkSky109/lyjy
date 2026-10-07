"""策略参数读取单测（LHJYPT_STRATEGY_PARAMS 注入）。"""
import importlib
import os

import lhjypt.runtime.params as params


def _reload():
    return importlib.reload(params)


def test_load_params_from_env(monkeypatch):
    monkeypatch.setenv("LHJYPT_STRATEGY_PARAMS",
                       '{"fast":10,"slow":20,"source":"optimizer"}')
    module = _reload()
    data = module.load_params()
    assert data["fast"] == 10
    assert data["slow"] == 20
    assert module.get_param("fast") == 10
    assert module.get_param("missing", 7) == 7


def test_load_params_missing_or_invalid(monkeypatch):
    monkeypatch.delenv("LHJYPT_STRATEGY_PARAMS", raising=False)
    module = _reload()
    assert module.load_params() == {}
    assert module.get_param("fast", 5) == 5

    monkeypatch.setenv("LHJYPT_STRATEGY_PARAMS", "not-json")
    module = _reload()
    assert module.load_params() == {}  # 非法 JSON 不抛异常

    monkeypatch.setenv("LHJYPT_STRATEGY_PARAMS", "[1,2,3]")
    module = _reload()
    assert module.load_params() == {}  # 非对象同样返回空