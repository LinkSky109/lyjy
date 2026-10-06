package com.lhjypt.core.service.capacity;

import com.alibaba.fastjson2.JSON;
import com.lhjypt.core.config.CapacityProperties;
import com.lhjypt.core.config.DistributedLock;
import com.lhjypt.core.mapper.CapacitySnapshotMapper;
import com.lhjypt.core.model.capacity.CapacitySnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 容量管理与自动伸缩（P2 模块五）。
 *
 * <p>定时采集资源/业务指标 → 综合水位（max{CPU,内存,磁盘}）→
 * {@code NORMAL/WARN(≥8成)/SCALE(≥9成)}；SCALE 时经 {@link AutoScaler} 扩容
 * （本地 Noop、生产 K8s HPA，扩容零中断由调度器保证）。快照落库供趋势分析。
 */
@Service
@RequiredArgsConstructor
public class CapacityService {

    private final CapacitySnapshotMapper snapshotMapper;
    private final CapacityProperties properties;
    private final CapacityMetricsProvider metricsProvider;
    private final AutoScaler autoScaler;
    private final DistributedLock distributedLock;

    @Scheduled(fixedRateString = "${lhjypt.capacity.sample-interval-ms:30000}")
    public void scheduledCollect() {
        distributedLock.execute("capacity.sample", this::collect);
    }

    @Transactional
    public CapacitySnapshot collect() {
        CapacityMetrics metrics = metricsProvider.sample();

        double loadPct = Math.max(metrics.cpuPct(), Math.max(metrics.memPct(), metrics.diskPct()));
        String status;
        if (loadPct >= properties.getScalePct() * 100) {
            status = CapacitySnapshot.SCALE;
        } else if (loadPct >= properties.getWarnPct() * 100) {
            status = CapacitySnapshot.WARN;
        } else {
            status = CapacitySnapshot.NORMAL;
        }

        int desired = status.equals(CapacitySnapshot.SCALE)
                ? properties.getDesiredMax()
                : status.equals(CapacitySnapshot.WARN) ? properties.getDesiredMin()
                : properties.getDesiredMin();

        CapacitySnapshot snapshot = new CapacitySnapshot();
        snapshot.setSampleTime(LocalDateTime.now());
        snapshot.setCpuPct(toDecimal(metrics.cpuPct()));
        snapshot.setMemPct(toDecimal(metrics.memPct()));
        snapshot.setDiskPct(toDecimal(metrics.diskPct()));
        snapshot.setActiveStrategies(metrics.activeStrategies());
        snapshot.setQueueDepth(metrics.queueDepth());
        snapshot.setInboundTps(metrics.inboundTps());
        snapshot.setLoadPct(toDecimal(loadPct));
        snapshot.setStatus(status);
        snapshot.setDesiredReplicas(desired);
        snapshot.setDetail(detailJson(metrics, status, desired));
        snapshotMapper.insert(snapshot);

        if (CapacitySnapshot.SCALE.equals(status) && properties.isAutoscaleEnabled()) {
            autoScaler.scaleTo(desired);
        }
        return snapshot;
    }

    public CapacitySnapshot latest() {
        return snapshotMapper.selectLatest();
    }

    public List<CapacitySnapshot> history(LocalDateTime from, LocalDateTime to) {
        LocalDateTime effectiveFrom = from == null ? LocalDateTime.now().minusHours(24) : from;
        LocalDateTime effectiveTo = to == null ? LocalDateTime.now() : to;
        return snapshotMapper.selectHistory(effectiveFrom, effectiveTo);
    }

    private static String detailJson(CapacityMetrics metrics, String status, int desired) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("cpuPct", round(metrics.cpuPct()));
        detail.put("memPct", round(metrics.memPct()));
        detail.put("diskPct", round(metrics.diskPct()));
        detail.put("activeStrategies", metrics.activeStrategies());
        detail.put("queueDepth", metrics.queueDepth());
        detail.put("inboundTps", metrics.inboundTps());
        detail.put("status", status);
        detail.put("desiredReplicas", desired);
        return JSON.toJSONString(detail);
    }

    private static BigDecimal toDecimal(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }

    private static double round(double value) {
        return Math.round(value * 100d) / 100d;
    }
}