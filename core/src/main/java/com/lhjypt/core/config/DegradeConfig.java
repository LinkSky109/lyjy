package com.lhjypt.core.config;

import com.lhjypt.core.service.degrade.DegradeService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.function.LongSupplier;

/**
 * 性能降级模块配置（P1）
 */
@Configuration
public class DegradeConfig {

    /** 纳秒时钟（限流/熔断窗口用；生产可替换为 Redis 时间源） */
    @Bean
    public LongSupplier nanoClock() {
        return System::nanoTime;
    }

    /** 启动时注册默认降级开关与熔断器 */
    @Bean
    public org.springframework.boot.ApplicationRunner degradeBootstrap(DegradeService degradeService) {
        return args -> degradeService.registerDefaults();
    }
}
