package com.lhjypt.core.service.strategy;

/**
 * 沙箱启动请求。
 *
 * @param strategyId   策略 ID
 * @param sourceCode   解密后的 Python 源码（仅进程内传递）
 * @param paramsJson   策略参数 JSON
 * @param oneshot      执行完是否退出（true=一次性任务，false=长驻）
 */
public record SandboxRequest(String strategyId,
                             String sourceCode,
                             String paramsJson,
                             boolean oneshot) {
}
