package com.lhjypt.core.service.strategy;

import com.lhjypt.core.config.SandboxProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * 容器沙箱镜像治理：按策略确保镜像可用。
 *
 * <p>{@code dockerPullPolicy}：
 * <ul>
 *   <li>{@code NEVER}：仅使用本地镜像，缺失直接失败（离线/受控环境）。</li>
 *   <li>{@code IF_NOT_PRESENT}（默认）：本地不存在才拉取。</li>
 *   <li>{@code ALWAYS}：每次拉取，保证最新。</li>
 * </ul>
 * 失败抛出带可执行指引的 {@link IllegalStateException}，避免沙箱以隐晦错误启动。
 */
@Component
public class SandboxImageManager {

    private static final Logger log = LoggerFactory.getLogger(SandboxImageManager.class);
    private static final long INSPECT_TIMEOUT_SECONDS = 30;

    private final SandboxProperties properties;
    private final CommandRunner commandRunner;

    @org.springframework.beans.factory.annotation.Autowired
    public SandboxImageManager(SandboxProperties properties) {
        this(properties, SandboxImageManager::runProcess);
    }

    /** 供测试注入自定义命令执行器。 */
    SandboxImageManager(SandboxProperties properties, CommandRunner commandRunner) {
        this.properties = properties;
        this.commandRunner = commandRunner;
    }

    /** 确保 {@code dockerImage} 按策略可用；不可用则抛异常。 */
    public void ensureImage() {
        String image = properties.getDockerImage();
        String policy = properties.getDockerPullPolicy() == null
                ? "IF_NOT_PRESENT" : properties.getDockerPullPolicy().trim().toUpperCase(Locale.ROOT);

        switch (policy) {
            case "NEVER" -> {
                if (!imageExists(image)) {
                    throw new IllegalStateException("沙箱镜像不存在且策略为 NEVER: " + image
                            + "，请先执行 docker pull " + image + " 或调整 docker-pull-policy");
                }
            }
            case "ALWAYS" -> pull(image);
            default -> {
                if (!imageExists(image)) {
                    pull(image);
                }
            }
        }
    }

    private boolean imageExists(String image) {
        return commandRunner.run(
                List.of(properties.getDockerBin(), "image", "inspect", image),
                INSPECT_TIMEOUT_SECONDS) == 0;
    }

    private void pull(String image) {
        log.info("拉取沙箱镜像: {}", image);
        int code = commandRunner.run(
                List.of(properties.getDockerBin(), "pull", image),
                properties.getDockerPullTimeoutSeconds());
        if (code != 0) {
            throw new IllegalStateException("拉取沙箱镜像失败(" + code + "): " + image);
        }
    }

    private static int runProcess(List<String> command, long timeoutSeconds) {
        try {
            ProcessBuilder builder = new ProcessBuilder(command)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD);
            Process process = builder.start();
            if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return -1;
            }
            return process.exitValue();
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            return -1;
        } catch (Exception error) {
            return -1;
        }
    }
}
