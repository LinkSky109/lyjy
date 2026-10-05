package com.lhjypt.core.service.monitor;

import com.lhjypt.core.mapper.MetricSampleMapper;
import com.lhjypt.core.model.monitor.MetricSample;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.ThreadMXBean;
import java.time.LocalDateTime;

/**
 * 指标采样服务
 * 定时采集 JVM 和系统指标
 */
@Service
public class MetricSampler {
    
    private final MetricSampleMapper metricSampleMapper;
    private final MemoryMXBean memoryMXBean;
    private final OperatingSystemMXBean osMXBean;
    private final ThreadMXBean threadMXBean;
    
    public MetricSampler(MetricSampleMapper metricSampleMapper) {
        this.metricSampleMapper = metricSampleMapper;
        this.memoryMXBean = ManagementFactory.getMemoryMXBean();
        this.osMXBean = ManagementFactory.getOperatingSystemMXBean();
        this.threadMXBean = ManagementFactory.getThreadMXBean();
    }
    
    /**
     * 定时采样（默认每10秒）
     */
    @Scheduled(fixedRateString = "${lhjypt.monitoring.sample-interval:10000}")
    public void sample() {
        LocalDateTime now = LocalDateTime.now();
        String labels = "{}";
        
        // 1. JVM 内存使用
        sampleMetric(now, "jvm.memory.used", 
                (double) memoryMXBean.getHeapMemoryUsage().getUsed(), labels);
        
        // 2. JVM 内存最大值
        sampleMetric(now, "jvm.memory.max",
                (double) memoryMXBean.getHeapMemoryUsage().getMax(), labels);
        
        // 3. 系统 CPU 负载
        sampleMetric(now, "system.cpu.load",
                osMXBean.getSystemLoadAverage(), labels);
        
        // 4. 可用处理器数
        sampleMetric(now, "system.cpu.count",
                (double) osMXBean.getAvailableProcessors(), labels);
        
        // 5. JVM 线程数
        sampleMetric(now, "jvm.threads.count",
                (double) threadMXBean.getThreadCount(), labels);
    }
    
    /**
     * 采样单个指标
     */
    private void sampleMetric(LocalDateTime ts, String metric, Double value, String labels) {
        try {
            MetricSample sample = new MetricSample();
            sample.setTs(ts);
            sample.setMetric(metric);
            sample.setValue(value);
            sample.setLabels(labels);
            metricSampleMapper.insert(sample);
        } catch (Exception e) {
            // ClickHouse 未启动时忽略错误
        }
    }
    
    /**
     * 手动记录业务指标
     */
    public void recordMetric(String metric, Double value, String labels) {
        sampleMetric(LocalDateTime.now(), metric, value, labels);
    }
}
