"""本地子进程沙箱的 API 白名单守卫（best-effort）。

生产应使用容器/Serverless 沙箱（SandboxExecutor 可替换实现）。
本守卫：
- 阻止导入 subprocess/ctypes/socket/shutil/multiprocessing/pty；
- 通过 sys.addaudithook 拦截网络连接、进程创建、系统调用等审计事件。
"""
import builtins
import sys

# 仅阻止明确危险的模块；socket/shutil 是标准库内部依赖（tempfile/urllib 等），
# 不能在此拦截，否则会破坏合法 SDK 导入——网络/系统调用改由审计钩子按“操作”拦截。
_BLOCKED_MODULES = {
    "subprocess", "ctypes", "multiprocessing", "pty", "lib2to3",
}

_BLOCKED_AUDIT_PREFIXES = (
    "socket.", "subprocess.", "os.system", "os.exec", "os.spawn", "os.fork",
    "ctypes.dlopen", "shutil.rmtree", "ftplib.", "http.client.",
)

_original_import = builtins.__import__


def _guarded_import(name, *args, **kwargs):
    root = name.split(".")[0]
    if root in _BLOCKED_MODULES:
        raise ImportError("sandbox forbids import: " + name)
    return _original_import(name, *args, **kwargs)


def _audit(event, args):
    for prefix in _BLOCKED_AUDIT_PREFIXES:
        if event == prefix or event.startswith(prefix):
            raise PermissionError("sandbox forbids: " + event)


def install():
    builtins.__import__ = _guarded_import
    if hasattr(sys, "addaudithook"):
        sys.addaudithook(_audit)
