package com.lhjypt.core.service.strategy;

import com.lhjypt.core.config.SandboxProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 镜像治理策略测试：NEVER / IF_NOT_PRESENT / ALWAYS，以及拉取失败快速失败。
 * 通过注入 {@link CommandRunner} 断言实际执行的 docker 命令。
 */
class SandboxImageManagerTest {

    @Test
    @DisplayName("NEVER + 本地缺失：抛异常且不拉取")
    void neverFailsWhenImageMissing() {
        FakeRunner runner = new FakeRunner(false, 0);
        SandboxImageManager manager = manager("NEVER", runner);

        assertThatThrownBy(manager::ensureImage)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("NEVER");
        assertThat(runner.hasCommand("pull")).isFalse();
    }

    @Test
    @DisplayName("NEVER + 本地存在：通过")
    void neverPassesWhenPresent() {
        FakeRunner runner = new FakeRunner(true, 0);
        manager("NEVER", runner).ensureImage();
        assertThat(runner.hasCommand("inspect")).isTrue();
        assertThat(runner.hasCommand("pull")).isFalse();
    }

    @Test
    @DisplayName("IF_NOT_PRESENT + 本地缺失：先 inspect 再 pull")
    void ifNotPresentPullsWhenMissing() {
        FakeRunner runner = new FakeRunner(false, 0);
        manager("IF_NOT_PRESENT", runner).ensureImage();
        assertThat(runner.hasCommand("inspect")).isTrue();
        assertThat(runner.hasCommand("pull")).isTrue();
    }

    @Test
    @DisplayName("IF_NOT_PRESENT + 本地存在：不拉取")
    void ifNotPresentSkipsPullWhenPresent() {
        FakeRunner runner = new FakeRunner(true, 0);
        manager("IF_NOT_PRESENT", runner).ensureImage();
        assertThat(runner.hasCommand("pull")).isFalse();
    }

    @Test
    @DisplayName("ALWAYS：始终拉取")
    void alwaysPulls() {
        FakeRunner runner = new FakeRunner(true, 0);
        manager("ALWAYS", runner).ensureImage();
        assertThat(runner.hasCommand("pull")).isTrue();
    }

    @Test
    @DisplayName("拉取失败：抛异常")
    void pullFailureThrows() {
        FakeRunner runner = new FakeRunner(false, 1);
        assertThatThrownBy(() -> manager("IF_NOT_PRESENT", runner).ensureImage())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("拉取沙箱镜像失败");
    }

    private static SandboxImageManager manager(String policy, CommandRunner runner) {
        SandboxProperties properties = new SandboxProperties();
        properties.setDockerBin("docker");
        properties.setDockerImage("python:3.11-alpine");
        properties.setDockerPullPolicy(policy);
        return new SandboxImageManager(properties, runner);
    }

    private static final class FakeRunner implements CommandRunner {

        private final boolean imageExists;
        private final int pullExitCode;
        private final List<List<String>> calls = new ArrayList<>();

        private FakeRunner(boolean imageExists, int pullExitCode) {
            this.imageExists = imageExists;
            this.pullExitCode = pullExitCode;
        }

        @Override
        public int run(List<String> command, long timeoutSeconds) {
            calls.add(command);
            if (command.contains("inspect")) {
                return imageExists ? 0 : 1;
            }
            if (command.contains("pull")) {
                return pullExitCode;
            }
            return 0;
        }

        private boolean hasCommand(String verb) {
            return calls.stream().anyMatch(c -> c.contains(verb));
        }
    }
}
