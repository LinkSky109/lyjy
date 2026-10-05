package com.lhjypt.core.service.strategy;

import com.lhjypt.core.config.SandboxProperties;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 本地受限子进程沙箱实现（P1）。
 *
 * <ul>
 *   <li>独立临时工作目录，源码写入 {@code strategy.py}；</li>
 *   <li>{@code ulimit} 限制 CPU 时间与虚拟内存（best-effort）；</li>
 *   <li>运行超时（默认 30s）强制 {@code destroyForcibly}；</li>
 *   <li>Python 侧审计钩子 + 导入白名单，禁网络/子进程/系统调用（best-effort）；</li>
 *   <li>同一策略不并发运行；单策略崩溃不影响 core；</li>
 *   <li>stdout/stderr 落文件供状态查询，输出不被 core 执行。</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class ProcessSandboxExecutor implements SandboxExecutor {

    private static final Logger log = LoggerFactory.getLogger(ProcessSandboxExecutor.class);

    private final SandboxProperties properties;
    private final Map<String, SandboxHandle> handles = new ConcurrentHashMap<>();

    @Override
    public SandboxHandle start(SandboxRequest request) {
        String strategyId = request.strategyId();
        SandboxHandle existing = handles.get(strategyId);
        if (existing != null && existing.isRunning()) {
            throw new IllegalStateException("策略已在运行: " + strategyId);
        }
        try {
            Path workDir = Files.createTempDirectory("lhjypt-sandbox-");
            Files.writeString(workDir.resolve("strategy.py"),
                    request.sourceCode() == null ? "" : request.sourceCode());
            copyResource("sandbox/sandbox_guard.py", workDir.resolve("sandbox_guard.py"));
            copyResource("sandbox/run_sandbox.py", workDir.resolve("run_sandbox.py"));

            String command = String.format(
                    "ulimit -t %d -v %d 2>/dev/null; exec \"$PY\" run_sandbox.py",
                    properties.getMaxCpuSeconds(),
                    (long) properties.getMaxMemoryMb() * 1024);
            ProcessBuilder builder = new ProcessBuilder("sh", "-c", command);
            builder.directory(workDir.toFile());
            builder.redirectOutput(workDir.resolve("stdout.log").toFile());
            builder.redirectError(workDir.resolve("stderr.log").toFile());
            Map<String, String> env = builder.environment();
            env.put("PY", properties.getPython());
            env.put("PYTHONPATH", new File(properties.getSdkPath()).getAbsolutePath());
            // 实时刷新输出，保证强杀时已产生的日志可被状态查询读取
            env.put("PYTHONUNBUFFERED", "1");
            env.put("PYTHONDONTWRITEBYTECODE", "1");
            env.put("LHJYPT_SANDBOX_ONESHOT", request.oneshot() ? "1" : "0");
            if (request.paramsJson() != null) {
                env.put("LHJYPT_STRATEGY_PARAMS", request.paramsJson());
            }

            Process process = builder.start();
            SandboxHandle handle = new SandboxHandle(
                    strategyId, process.pid(), workDir, process);
            handle.setStatus(SandboxHandle.RUNNING);
            handles.put(strategyId, handle);
            monitor(handle);
            log.info("策略沙箱已启动: id={} pid={} dir={}",
                    strategyId, process.pid(), workDir);
            return handle;
        } catch (IOException error) {
            throw new IllegalStateException("启动策略沙箱失败: " + error.getMessage(), error);
        }
    }

    /** 独立守护线程监控退出与超时强杀。 */
    private void monitor(SandboxHandle handle) {
        Thread thread = new Thread(() -> {
            try {
                boolean finished = handle.getProcess()
                        .waitFor(properties.getTimeoutSeconds(), TimeUnit.SECONDS);
                if (!finished) {
                    handle.getProcess().destroyForcibly();
                    handle.complete(SandboxHandle.TIMEOUT, null);
                    log.warn("策略沙箱超时强杀: id={}", handle.getStrategyId());
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
        }, "sandbox-monitor-" + handle.getStrategyId());
        thread.setDaemon(true);
        thread.start();
    }

    @Override
    public boolean stop(String strategyId) {
        SandboxHandle handle = handles.get(strategyId);
        if (handle == null) {
            return false;
        }
        Process process = handle.getProcess();
        if (process.isAlive()) {
            process.destroyForcibly();
            try {
                process.waitFor(5, TimeUnit.SECONDS);
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
            }
        }
        handle.markStopped(process.isAlive() ? null : process.exitValue());
        log.info("策略沙箱已停止: id={}", strategyId);
        return true;
    }

    @Override
    public SandboxHandle status(String strategyId) {
        return handles.get(strategyId);
    }

    /** 读取沙箱输出尾部（stdout+stderr），不执行任何内容。 */
    public String outputTail(String strategyId) {
        SandboxHandle handle = handles.get(strategyId);
        if (handle == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (String name : new String[]{"stdout.log", "stderr.log"}) {
            Path path = handle.getWorkDir().resolve(name);
            if (Files.exists(path)) {
                try {
                    byte[] all = Files.readAllBytes(path);
                    int from = Math.max(0, all.length - properties.getOutputTailBytes());
                    builder.append(new String(all, from, all.length - from));
                } catch (IOException ignored) {
                    // 输出读取失败不影响状态查询
                }
            }
        }
        return builder.toString();
    }

    private void copyResource(String classpath, Path target) throws IOException {
        try (InputStream input = new ClassPathResource(classpath).getInputStream()) {
            Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
