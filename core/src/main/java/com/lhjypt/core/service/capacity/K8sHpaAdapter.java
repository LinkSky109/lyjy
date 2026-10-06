package com.lhjypt.core.service.capacity;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * K8s HPA 适配预留：记录期望副本并驱动 HPA 注解（生产接入后替换为真实 API）。
 * 扩容由 K8s 保证零中断（滚动更新）。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "lhjypt.capacity.autoscale-provider", havingValue = "k8s")
public class K8sHpaAdapter implements AutoScaler {

    @Override
    public void scaleTo(int desiredReplicas) {
        log.warn("[capacity] K8s HPA 期望副本 -> {}（适配器预留，未真实调用集群）", desiredReplicas);
    }
}