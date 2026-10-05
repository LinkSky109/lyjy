package com.lhjypt.core.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 策略沙箱配置（P1）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "lhjypt.strategy.sandbox")
public class SandboxProperties {

    /** Python 解释器（生产可换容器执行器）。 */
    private String python = "python3";

    /** 策略 SDK 路径，注入子进程 PYTHONPATH。 */
    private String sdkPath = "strategy-sdk";

    /** 运行超时（秒），超时强杀。 */
    private int timeoutSeconds = 30;

    /** 内存上限（MB），经 ulimit -v 限制（best-effort）。 */
    private int maxMemoryMb = 256;

    /** CPU 时间上限（秒），经 ulimit -t 限制（best-effort）。 */
    private int maxCpuSeconds = 30;

    /** 状态接口返回的输出尾部字节数。 */
    private int outputTailBytes = 4096;

    /** 执行器实现：process（本地受限子进程，默认）| docker（容器沙箱，生产）。 */
    private String executor = "process";

    /** Docker 镜像（容器执行器）。 */
    private String dockerImage = "python:3.11-alpine";

    /** docker 可执行文件。 */
    private String dockerBin = "docker";

    /** 容器 CPU 限额。 */
    private double dockerCpus = 1.0;

    /** 容器 PID 上限。 */
    private int dockerPidsLimit = 128;

    /** 容器网络模式（默认 none，禁网络）。 */
    private String dockerNetwork = "none";

    /**
     * 容器沙箱临时工作目录基路径（bind mount 源）。
     * 空=系统临时目录（Linux 推荐；Docker Desktop(macOS/Windows) 下 {@code /var/folders} 不会被共享，
     * 需指向共享路径下的目录，如 {@code /Users/<user>/...}）。
     */
    private String dockerWorkDir = "";
}
