package com.lhjypt.core.service.capacity;

/**
 * 自动伸缩适配 SPI：生产可对接 K8s HPA / 自定义调度（扩容零中断由调度器保证）。
 */
public interface AutoScaler {

    void scaleTo(int desiredReplicas);
}