package com.lhjypt.core.service.strategy;

import com.lhjypt.core.config.SandboxProperties;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 容器化沙箱执行器（生产向）。
 *
 * <p>在独立临时工作目录写入 {@code strategy.py}，并把 classpath 下的
 * {@code sandbox/sandbox_guard.py}、{@code sandbox/run_sandbox.py} 复制到工作目录，
 * 以只读/网络禁用/CPU-内存-PID 受限的容器执行：
 *
 * <pre>{@code
 * docker run --rm --name lhjypt-sandbox-<id> --network none --memory 256m \
 *   --cpus 1.0 --pids-limit 128 -e PYTHONUNBUFFERED=1 \
 *   -e LHJYPT_SANDBOX_ONESHOT=<0|1> -e PYTHONPATH=/sdk \
 *   -v <workDir>:/sandbox [-v <sdk>:/sdk:ro] -w /sandbox <image> python run_sandbox.py
 * }</pre>
 *
 * <p>与 {@link ProcessSandboxExecutor} 对外行为一致：同一策略不并发运行、超时强杀、
 * stdout/stderr 落文件供状态查询。容器无直接 pid，句柄 pid 记为 0。
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "lhjypt.strategy.sandbox.executor", havingValue = "docker")
public class DockerSandboxExecutor implements SandboxExecutor {

    private static final Logger log = LoggerFactory.getLogger(DockerSandboxExecutor.class);
    private static final String CONTAINER_PREFIX = "lhjypt-sandbox-";
    private static final long REMOVE_TIMEOUT_SECONDS = 15;

    private final SandboxProperties properties;
    private final SandboxImageManager imageManager;
    private final Map<String, SandboxHandle> handles = new ConcurrentHashMap<>();

    @Override
    public SandboxHandle start(SandboxRequest request) {
        String strategyId = request.strategyId();
        SandboxHandle existing = handles.get(strategyId);
        if (existing != null && existing.isRunning()) {
            throw new IllegalStateException("策略已在运行: " + strategyId);
        }
        // 启动前按策略确保镜像可用（NEVER/IF_NOT_PRESENT/ALWAYS），失败快速给出可执行指引
        imageManager.ensureImage();
        String containerName = containerName(strategyId);
        try {
            Path workDir = createWorkDir();
            Files.writeString(workDir.resolve("strategy.py"),
                    request.sourceCode() == null ? "" : request.sourceCode());
            copyResource("sandbox/sandbox_guard.py", workDir.resolve("sandbox_guard.py"));
            copyResource("sandbox/run_sandbox.py", workDir.resolve("run_sandbox.py"));

            ProcessBuilder builder = new ProcessBuilder(buildCommand(request, workDir, containerName));
            builder.directory(workDir.toFile());
            builder.redirectOutput(workDir.resolve("stdout.log").toFile());
            builder.redirectError(workDir.resolve("stderr.log").toFile());

            Process process = builder.start();
            SandboxHandle handle = new SandboxHandle(strategyId, 0L, workDir, process);
            handle.setStatus(SandboxHandle.RUNNING);
            handles.put(strategyId, handle);
            monitor(handle, containerName);
            log.info("策略容器沙箱已启动: id={} container={} dir={}",
                    strategyId, containerName, workDir);
            return handle;
        } catch (IOException error) {
            throw new IllegalStateException("启动策略容器沙箱失败: " + error.getMessage(), error);
        }
    }

    private List<String> buildCommand(SandboxRequest request, Path workDir, String containerName) {
        List<String> command = new ArrayList<>();
        command.add(properties.getDockerBin());
        command.add("run");
        command.add("--rm");
        command.add("--name");
        command.add(containerName);
        command.add("--network");
        command.add(properties.getDockerNetwork());
        command.add("--memory");
        command.add(properties.getMaxMemoryMb() + "m");
        command.add("--cpus");
        command.add(String.valueOf(properties.getDockerCpus()));
        command.add("--pids-limit");
        command.add(String.valueOf(properties.getDockerPidsLimit()));
        command.add("-e");
        command.add("PYTHONUNBUFFERED=1");
        command.add("-e");
        command.add("LHJYPT_SANDBOX_ONESHOT=" + (request.oneshot() ? "1" : "0"));
        if (request.paramsJson() != null && !request.paramsJson().isBlank()) {
            command.add("-e");
            command.add("LHJYPT_STRATEGY_PARAMS=" + request.paramsJson());
        }
        command.add("-e");
        // SDK 挂载到顶层 /sdk：嵌套挂载点(/sandbox/sdk)在部分 Docker 运行时无法创建
        command.add("PYTHONPATH=/sdk");
        command.add("-v");
        command.add(workDir.toAbsolutePath() + ":/sandbox");
        Path sdk = Path.of(properties.getSdkPath() == null ? "" : properties.getSdkPath());
        if (Files.exists(sdk)) {
            command.add("-v");
            command.add(sdk.toAbsolutePath() + ":/sdk:ro");
        }
        command.add("-w");
        command.add("/sandbox");
        command.add(properties.getDockerImage());
        command.add("python");
        command.add("run_sandbox.py");
        return command;
    }

    /** 独立守护线程监控退出与超时强杀（超时先删容器，再回收 docker 客户端进程）。 */
    private void monitor(SandboxHandle handle, String containerName) {
        Thread thread = new Thread(() -> {
            try {
                boolean finished = handle.getProcess()
                        .waitFor(properties.getTimeoutSeconds(), TimeUnit.SECONDS);
                if (!finished) {
                    removeContainer(containerName);
                    handle.getProcess().destroyForcibly();
                    handle.complete(SandboxHandle.TIMEOUT, null);
                    log.warn("策略容器沙箱超时强杀: id={} container={}",
                            handle.getStrategyId(), containerName);
                } else {
                    int exit = handle.getProcess().exitValue();
                    handle.complete(exit == 0
                            ? SandboxHandle.EXITED : SandboxHandle.FAILED, exit);
                }
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
            } catch (Exception error) {
                handle.complete(SandboxHandle.FAILED, null);
            }
        }, "docker-sandbox-monitor-" + handle.getStrategyId());
        thread.setDaemon(true);
        thread.start();
    }

    @Override
    public boolean stop(String strategyId) {
        SandboxHandle handle = handles.get(strategyId);
        if (handle == null) {
            return false;
        }
        removeContainer(containerName(strategyId));
        Process process = handle.getProcess();
        if (process != null && process.isAlive()) {
            try {
                process.waitFor(5, TimeUnit.SECONDS);
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
            }
        }
        handle.markStopped(process != null && !process.isAlive() ? process.exitValue() : null);
        log.info("策略容器沙箱已停止: id={}", strategyId);
        return true;
    }

    @Override
    public SandboxHandle status(String strategyId) {
        return handles.get(strategyId);
    }

    /** 读取沙箱输出尾部（stdout+stderr），不执行任何内容。 */
    @Override
    public String outputTail(String strategyId) {
        SandboxHandle handle = handles.get(strategyId);
        if (handle == null || handle.getWorkDir() == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (String name : new String[]{"stdout.log", "stderr.log"}) {
            builder.append(readTail(handle.getWorkDir().resolve(name)));
        }
        return builder.toString();
    }

    /** 读取文件尾部最多 outputTailBytes 字节。 */
    private String readTail(Path path) {
        if (!Files.exists(path)) {
            return "";
        }
        try {
            byte[] all = Files.readAllBytes(path);
            int from = Math.max(0, all.length - properties.getOutputTailBytes());
            return new String(all, from, all.length - from, StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            // 输出读取失败不影响状态查询
            return "";
        }
    }

    /** 强制删除容器；best-effort，不抛异常。 */
    private void removeContainer(String containerName) {
        try {
            Process process = new ProcessBuilder(
                    properties.getDockerBin(), "rm", "-f", containerName)
                    .redirectErrorStream(true)
                    .start();
            if (!process.waitFor(REMOVE_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
        } catch (Exception error) {
            log.debug("删除容器失败 {}: {}", containerName, error.getMessage());
        }
    }

    private static String containerName(String strategyId) {
        return CONTAINER_PREFIX + sanitize(strategyId);
    }

    /** Docker 容器名只允许 [a-zA-Z0-9][a-zA-Z0-9_.-]，非法字符替换为 '-'。 */
    private static String sanitize(String strategyId) {
        String value = strategyId == null ? "" : strategyId.toLowerCase(Locale.ROOT);
        value = value.replaceAll("[^a-z0-9_.-]", "-");
        return value.isBlank() ? "strategy" : value;
    }

    private void copyResource(String classpath, Path target) throws IOException {
        try (InputStream input = new ClassPathResource(classpath).getInputStream()) {
            Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * 创建容器 bind mount 的工作目录。
     *
     * <p>默认使用系统临时目录（适合 Linux 宿主机）；Docker Desktop(macOS/Windows)
     * 不共享 {@code /var/folders} 等系统临时目录，需通过 {@code docker-work-dir}
     * 指向共享路径下的目录，否则容器内会看到空目录。
     */
    private Path createWorkDir() throws IOException {
        String configured = properties.getDockerWorkDir();
        if (configured == null || configured.isBlank()) {
            return Files.createTempDirectory("lhjypt-sandbox-docker-");
        }
        Path base = Path.of(configured).toAbsolutePath();
        Files.createDirectories(base);
        return Files.createTempDirectory(base, "lhjypt-sandbox-docker-");
    }
}
