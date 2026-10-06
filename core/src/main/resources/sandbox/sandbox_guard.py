"""本地子进程沙箱的 API 白名单守卫（best-effort）。

生产应使用容器/Serverless 沙箱（SandboxExecutor 可替换实现）。
本守卫：
- 阻止导入 subprocess/ctypes/socket/shutil/multiprocessing/pty；
- 通过 sys.addaudithook 拦截网络连接、进程创建、系统调用等审计事件；
- 例外：仅放行到本平台本地 API 的回环请求（127.0.0.1/::1/localhost:8081），
  供策略读写行情/账户/下单；任何外部网络仍被禁止（防数据外泄与横向移动）。
"""
import builtins
import sys

# 仅阻止明确危险的模块；socket/shutil 是标准库内部依赖（tempfile/urllib 等），
# 不能在此拦截，否则会破坏合法 SDK 导入——网络/系统调用改由审计钩子按“操作”拦截。
_BLOCKED_MODULES = {
    "subprocess", "ctypes", "multiprocessing", "pty", "lib2to3",
}

_BLOCKED_AUDIT_PREFIXES = (
    "subprocess.", "os.system", "os.exec", "os.spawn", "os.fork",
    "ctypes.dlopen", "shutil.rmtree", "ftplib.",
)

# 连接/解析类事件：仅放行平台回环 8081，其余一律拒绝（外网全禁）。
# http.client.send/read 等不在此列：它们只能发生在已被判定的连接之上。
_NET_CONNECT_EVENTS = ("http.client.connect", "socket.connect", "socket.getaddrinfo")

# 平台本地 API 地址（沙箱允许策略回环访问的平台端点）
PLATFORM_API_HOSTS = ("127.0.0.1", "::1", "localhost")
PLATFORM_API_PORT = 8081

_original_import = builtins.__import__


def _guarded_import(name, *args, **kwargs):
    root = name.split(".")[0]
    if root in _BLOCKED_MODULES:
        raise ImportError("sandbox forbids import: " + name)
    return _original_import(name, *args, **kwargs)


def _extract_connect_target(event, args):
    """从网络审计事件中提取 (host, port)；非连接类事件返回 None。"""
    if event == "http.client.connect" and len(args) >= 3:
        return args[1], args[2]
    if event == "socket.getaddrinfo" and len(args) >= 2:
        return args[0], args[1]
    if event == "socket.connect":
        for arg in args:
            if isinstance(arg, (tuple, list)) and len(arg) >= 2 \
                    and isinstance(arg[0], str):
                return arg[0], arg[1]
        return None
    return None


def _is_platform_loopback(event, args):
    """连接/解析目标是否为平台本地 API（回环 8081）。"""
    target = _extract_connect_target(event, args)
    if target is None:
        return False
    host, port = target
    try:
        port = int(port)
    except (TypeError, ValueError):
        return False
    return host in PLATFORM_API_HOSTS and port == PLATFORM_API_PORT


def _audit(event, args):
    if event in _NET_CONNECT_EVENTS:
        if _is_platform_loopback(event, args):
            return
        raise PermissionError("sandbox forbids: " + event)
    for prefix in _BLOCKED_AUDIT_PREFIXES:
        if event == prefix or event.startswith(prefix):
            raise PermissionError("sandbox forbids: " + event)


def install():
    builtins.__import__ = _guarded_import
    if hasattr(sys, "addaudithook"):
        sys.addaudithook(_audit)
