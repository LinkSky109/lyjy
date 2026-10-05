package com.lhjypt.core.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 分布式调度锁配置（PRD §10.1/§10.2）。
 *
 * <p>多实例部署时，定时任务通过 Redis 锁保证同一时刻仅一个实例执行；
 * Redis 不可用时降级为“照常执行”（fail-open），不阻断业务调度。</p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "lhjypt.schedule.lock")
public class ScheduleLockProperties {

    /** 是否启用分布式调度锁；关闭时所有定时任务直接执行（本地/单实例）。 */
    private boolean enabled = true;

    /** 锁自动过期时间（秒），作为实例崩溃时的兜底释放，应大于任务最大执行时长。 */
    private int ttlSeconds = 120;
}
