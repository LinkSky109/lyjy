package com.lhjypt.core.service.capacity;

import com.lhjypt.core.mapper.StrategyInfoMapper;
import com.lhjypt.core.model.strategy.StrategyInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.nio.file.FileStore;
import java.nio.file.FileSystems;

/**
 * 本地 JVM 容量指标采集：CPU 负载 / 堆内存 / 磁盘 / 运行中策略数。
 * 生产可用 Prometheus/NRI 采集替换（CapacityMetricsProvider SPI）。
 */
@Component
@RequiredArgsConstructor
public class JvmCapacityMetricsProvider implements CapacityMetricsProvider {

    private final OperatingSystemMXBean os =
            ManagementFactory.getOperatingSystemMXBean();
    private final StrategyInfoMapper strategyInfoMapper;

    @Override
    public CapacityMetrics sample() {
        int cores = os.getAvailableProcessors();
        double load = os.getSystemLoadAverage();
        double cpuPct = load > 0 && cores > 0
                ? Math.min(100d, load / cores * 100d) : 0d;

        long total = Runtime.getRuntime().totalMemory();
        long free = Runtime.getRuntime().freeMemory();
        double memPct = total > 0 ? 100d * (total - free) / total : 0d;

        double diskPct = diskUsagePercent();

        int activeStrategies = safeCountRunning();
        return new CapacityMetrics(cpuPct, memPct, diskPct, activeStrategies, 0, 0);
    }

    private int safeCountRunning() {
        try {
            return strategyInfoMapper.countByStatus(StrategyInfo.RUNNING);
        } catch (Exception ignored) {
            return 0;
        }
    }

    private double diskUsagePercent() {
        try {
            for (FileStore store : FileSystems.getDefault().getFileStores()) {
                long total = store.getTotalSpace();
                if (total > 0) {
                    return 100d * (total - store.getUsableSpace()) / total;
                }
            }
        } catch (Exception ignored) {
            // 磁盘不可用时该维贡献为 0
        }
        return 0d;
    }
}