package com.lhjypt.core.service.strategy;

import com.lhjypt.core.config.SandboxProperties;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 真实子进程沙箱测试；无 python3 时跳过，保证 CI 可移植。
 */
class ProcessSandboxExecutorTest {

    @Test
    @DisplayName("oneshot 策略执行后正常退出并捕获输出")
    void oneshotExecutesAndExits() {
        Assumptions.assumeTrue(pythonAvailable(), "python3 不可用，跳过沙箱进程测试");
        ProcessSandboxExecutor executor = new ProcessSandboxExecutor(properties(15));

        SandboxHandle handle = executor.start(new SandboxRequest(
                "S-ONESHOT", "print('hello-sandbox')\n", "{}", true));

        waitUntil(() -> !handle.isRunning(), 15_000);
        assertThat(handle.getStatus()).isEqualTo(SandboxHandle.EXITED);
        assertThat(handle.getExitCode()).isZero();
        assertThat(executor.outputTail("S-ONESHOT")).contains("hello-sandbox");
    }

    @Test
    @DisplayName("长驻策略并发启动被拒绝，停止后进程回收")
    void concurrentStartRejectedAndStop() {
        Assumptions.assumeTrue(pythonAvailable(), "python3 不可用，跳过沙箱进程测试");
        ProcessSandboxExecutor executor = new ProcessSandboxExecutor(properties(30));

        executor.start(new SandboxRequest("S-LONG",
                "import time\nwhile True:\n    time.sleep(0.2)\n", "{}", false));

        assertThatThrownBy(() -> executor.start(new SandboxRequest("S-LONG", "pass", "{}", false)))
                .isInstanceOf(IllegalStateException.class);

        assertThat(executor.stop("S-LONG")).isTrue();
        assertThat(executor.status("S-LONG").getStatus()).isEqualTo(SandboxHandle.STOPPED);
    }

    private static SandboxProperties properties(int timeoutSeconds) {
        SandboxProperties properties = new SandboxProperties();
        properties.setPython("python3");
        properties.setSdkPath("strategy-sdk");
        properties.setTimeoutSeconds(timeoutSeconds);
        return properties;
    }

    private static boolean pythonAvailable() {
        try {
            Process process = new ProcessBuilder("python3", "--version")
                    .redirectErrorStream(true).start();
            return process.waitFor(5, TimeUnit.SECONDS) && process.exitValue() == 0;
        } catch (Exception error) {
            return false;
        }
    }

    private static void waitUntil(BooleanSupplier condition, long timeoutMillis) {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }
}
