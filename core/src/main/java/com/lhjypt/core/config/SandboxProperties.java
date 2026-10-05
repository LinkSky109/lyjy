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
}
