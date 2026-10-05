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
 * 容器化沙箱测试；docker 不可用或镜像拉取失败时跳过，保证 CI 可移植。
 */
class DockerSandboxExecutorTest {

    private static final String IMAGE = "python:3.11-alpine";

    /** 仅在首次调用时探测 docker + 镜像，避免每个用例重复拉取。 */
    private static Boolean ready;

    @Test
    @DisplayName("oneshot 策略在容器内执行后正常退出并捕获输出")
    void oneshotExecutesInContainer() {
        Assumptions.assumeTrue(dockerReady(), "docker 不可用或镜像拉取失败，跳过容器沙箱测试");
        SandboxProperties properties = properties();
        DockerSandboxExecutor executor = new DockerSandboxExecutor(
                properties, new SandboxImageManager(properties));

        SandboxHandle handle = executor.start(new SandboxRequest(
                "S-DOCKER-ONESHOT", "print('hello-docker')\n", "{}", true));

        waitUntil(() -> !handle.isRunning(), 120_000);
        assertThat(handle.getStatus()).isEqualTo(SandboxHandle.EXITED);
        assertThat(handle.getExitCode()).isZero();
        assertThat(executor.outputTail("S-DOCKER-ONESHOT")).contains("hello-docker");
    }

    @Test
    @DisplayName("长驻策略并发启动被拒绝，停止后容器回收")
    void concurrentStartRejectedAndStop() {
        Assumptions.assumeTrue(dockerReady(), "docker 不可用或镜像拉取失败，跳过容器沙箱测试");
        SandboxProperties properties = properties();
        DockerSandboxExecutor executor = new DockerSandboxExecutor(
                properties, new SandboxImageManager(properties));

        executor.start(new SandboxRequest("S-DOCKER-LONG",
                "import time\nwhile True:\n    time.sleep(0.2)\n", "{}", false));

        assertThatThrownBy(() -> executor.start(new SandboxRequest(
                "S-DOCKER-LONG", "pass", "{}", false)))
                .isInstanceOf(IllegalStateException.class);

        assertThat(executor.stop("S-DOCKER-LONG")).isTrue();
        assertThat(executor.status("S-DOCKER-LONG").getStatus()).isEqualTo(SandboxHandle.STOPPED);
    }

    private static SandboxProperties properties() {
        SandboxProperties properties = new SandboxProperties();
        properties.setDockerBin("docker");
        properties.setDockerImage(IMAGE);
        properties.setDockerNetwork("none");
        properties.setTimeoutSeconds(60);
        // 不存在的 SDK 路径：不额外挂载 SDK 卷
        properties.setSdkPath("strategy-sdk");
        // Docker Desktop(macOS) 不共享系统临时目录 /var/folders，工作目录须在共享路径下
        properties.setDockerWorkDir(
                java.nio.file.Path.of("target", "docker-sandbox").toAbsolutePath().toString());
        return properties;
    }

    private static synchronized boolean dockerReady() {
        if (ready != null) {
            return ready;
        }
        ready = dockerInfoAvailable() && pullImage();
        return ready;
    }

    private static boolean dockerInfoAvailable() {
        try {
            Process process = new ProcessBuilder("docker", "info")
                    .redirectErrorStream(true).start();
            return process.waitFor(15, TimeUnit.SECONDS) && process.exitValue() == 0;
        } catch (Exception error) {
            return false;
        }
    }

    private static boolean pullImage() {
        try {
            Process process = new ProcessBuilder("docker", "pull", IMAGE)
                    .redirectErrorStream(true).start();
            return process.waitFor(180, TimeUnit.SECONDS) && process.exitValue() == 0;
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
                Thread.sleep(100);
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }
}
