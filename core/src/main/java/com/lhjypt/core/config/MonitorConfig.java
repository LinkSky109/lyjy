package com.lhjypt.core.config;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 监控配置
 * 启用定时任务调度，配置 Micrometer 指标注册表
 */
@Configuration
@EnableScheduling
public class MonitorConfig {

    /**
     * 自定义 Micrometer 指标注册表
     * 为所有指标添加公共标签
     */
    @Bean
    public MeterRegistryCustomizer<MeterRegistry> metricsCommonTags() {
        return registry -> registry.config()
                .commonTags("application", "lhjypt-core");
    }
}
