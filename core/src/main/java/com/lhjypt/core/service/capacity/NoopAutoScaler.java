package com.lhjypt.core.service.capacity;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 默认空伸缩：仅记录，不触发真实扩缩（本地单实例）。
 * 与 K8sHpaAdapter 通过 lhjypt.capacity.autoscale-provider 互斥激活。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "lhjypt.capacity.autoscale-provider",
        havingValue = "noop", matchIfMissing = true)
public class NoopAutoScaler implements AutoScaler {

    @Override
    public void scaleTo(int desiredReplicas) {
        log.info("[capacity] 建议伸缩至 {} 副本（Noop，未执行）", desiredReplicas);
    }
}