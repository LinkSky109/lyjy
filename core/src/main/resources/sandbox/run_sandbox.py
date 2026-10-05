"""沙箱入口：安装守卫后加载并执行 strategy.py。

- 默认保持进程存活，直到被平台 STOP（长驻策略）；
- 设置 LHJYPT_SANDBOX_ONESHOT=1 时执行完即退出（回测/一次性任务）。
策略源码由平台解密后写入同目录 strategy.py，不落日志。
"""
import os
import sys
import time
import traceback


def _install_guard():
    try:
        import sandbox_guard
        sandbox_guard.install()
    except Exception as error:  # noqa: BLE001
        print("[sandbox] guard install failed:", error, file=sys.stderr)


def _run_strategy(path):
    with open(path, "r", encoding="utf-8") as handle:
        code = handle.read()
    namespace = {"__name__": "__strategy__", "__file__": path}
    exec(compile(code, path, "exec"), namespace)

    try:
        from lhjypt.api.strategy import Strategy
    except ImportError:
        return
    for value in namespace.values():
        if isinstance(value, type) and issubclass(value, Strategy) and value is not Strategy:
            instance = value()
            instance.on_init()
            instance.on_start()
            print("[sandbox] strategy started:", value.__name__)
            return


def main():
    _install_guard()
    here = os.path.dirname(os.path.abspath(__file__))
    try:
        _run_strategy(os.path.join(here, "strategy.py"))
    except SystemExit:
        raise
    except Exception:  # noqa: BLE001
        traceback.print_exc()
        sys.exit(1)

    print("[sandbox] strategy source executed")
    if os.environ.get("LHJYPT_SANDBOX_ONESHOT") == "1":
        return
    while True:
        time.sleep(1)


if __name__ == "__main__":
    main()
