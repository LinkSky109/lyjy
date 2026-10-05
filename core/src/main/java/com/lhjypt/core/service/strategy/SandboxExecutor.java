package com.lhjypt.core.service.strategy;

/**
 * 策略沙箱执行器 SPI（P1）。
 *
 * <p>本地实现为受限子进程 {@link ProcessSandboxExecutor}；
 * 生产可实现为容器/Serverless 调度，对上层透明。
 */
public interface SandboxExecutor {

    /** 启动策略沙箱，返回运行句柄；同一策略已在运行时拒绝。 */
    SandboxHandle start(SandboxRequest request);

    /** 停止并回收策略沙箱；返回是否存在被停止的进程。 */
    boolean stop(String strategyId);

    /** 查询运行句柄；未运行时返回 null。 */
    SandboxHandle status(String strategyId);

    /**
     * 读取沙箱输出尾部（stdout+stderr），供状态查询展示；不执行任何内容。
     * 默认无输出，由具体执行器覆盖。
     */
    default String outputTail(String strategyId) {
        return "";
    }
}
