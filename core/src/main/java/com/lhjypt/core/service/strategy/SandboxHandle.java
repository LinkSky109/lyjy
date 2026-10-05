package com.lhjypt.core.service.strategy;

import java.nio.file.Path;
import java.time.LocalDateTime;

/**
 * 沙箱运行句柄（进程元数据 + 生命周期状态）。
 */
public class SandboxHandle {

    public static final String STARTING = "STARTING";
    public static final String RUNNING = "RUNNING";
    public static final String EXITED = "EXITED";
    public static final String FAILED = "FAILED";
    public static final String TIMEOUT = "TIMEOUT";
    public static final String STOPPED = "STOPPED";

    private final String strategyId;
    private final long pid;
    private final LocalDateTime startedAt;
    private final Path workDir;
    private final Process process;

    private volatile String status = STARTING;
    private volatile LocalDateTime finishedAt;
    private volatile Integer exitCode;

    public SandboxHandle(String strategyId, long pid, Path workDir, Process process) {
        this.strategyId = strategyId;
        this.pid = pid;
        this.workDir = workDir;
        this.process = process;
        this.startedAt = LocalDateTime.now();
    }

    public String getStrategyId() {
        return strategyId;
    }

    public long getPid() {
        return pid;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public Path getWorkDir() {
        return workDir;
    }

    public Process getProcess() {
        return process;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(LocalDateTime finishedAt) {
        this.finishedAt = finishedAt;
    }

    public Integer getExitCode() {
        return exitCode;
    }

    public void setExitCode(Integer exitCode) {
        this.exitCode = exitCode;
    }

    public boolean isRunning() {
        return STARTING.equals(status) || RUNNING.equals(status);
    }

    /** 原子完成：仅当仍处于运行态时写入终态，避免监控线程覆盖主动停止。 */
    public synchronized void complete(String terminalStatus, Integer code) {
        if (!isRunning()) {
            return;
        }
        this.status = terminalStatus;
        this.exitCode = code;
        this.finishedAt = LocalDateTime.now();
    }

    /** 主动停止（最高优先级终态）。 */
    public synchronized void markStopped(Integer code) {
        this.status = STOPPED;
        this.exitCode = code;
        this.finishedAt = LocalDateTime.now();
    }
}
