"""
事件引擎 - 策略内部事件驱动
"""
import queue
import threading
from typing import Callable, Dict, List


class EventEngine:
    """事件引擎

    支持事件注册与异步分发，用于策略内部模块解耦
    """

    def __init__(self):
        self._queue = queue.Queue()
        self._handlers: Dict[str, List[Callable]] = {}
        self._active = False
        self._thread: threading.Thread = None

    def register(self, event_type: str, handler: Callable):
        """注册事件处理器"""
        if event_type not in self._handlers:
            self._handlers[event_type] = []
        self._handlers[event_type].append(handler)

    def unregister(self, event_type: str, handler: Callable):
        """注销事件处理器"""
        if event_type in self._handlers:
            self._handlers[event_type].remove(handler)

    def put(self, event_type: str, data=None):
        """投递事件"""
        self._queue.put((event_type, data))

    def start(self):
        """启动事件引擎"""
        self._active = True
        self._thread = threading.Thread(target=self._run, daemon=True)
        self._thread.start()

    def stop(self):
        """停止事件引擎"""
        self._active = False
        if self._thread:
            self._thread.join(timeout=2)

    def _run(self):
        while self._active:
            try:
                event_type, data = self._queue.get(timeout=1)
                handlers = self._handlers.get(event_type, [])
                for handler in handlers:
                    try:
                        handler(data)
                    except Exception as e:
                        print(f"[EventEngine] handler error: {e}")
            except queue.Empty:
                continue
