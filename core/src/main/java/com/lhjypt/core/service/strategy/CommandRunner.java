package com.lhjypt.core.service.strategy;

import java.util.List;

/**
 * 外部命令执行 SPI：返回退出码；超时返回负值。抽象出来便于镜像治理逻辑做单元测试。
 */
@FunctionalInterface
public interface CommandRunner {

    int run(List<String> command, long timeoutSeconds);
}
