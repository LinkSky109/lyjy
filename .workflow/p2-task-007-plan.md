# TASK-P2-007 容量管理与自动伸缩适配

> 需求：requirements-supplement 模块五 §5.3/§5.5（水位监控、超阈值预警/扩容、扩容零中断）
> 依赖：P1 降级/通知、DistributedLock

## 节点
1. `capacity_snapshot` 水位快照表（迁移 p2_007）。
2. `CapacityService`：定时（30s，DistributedLock 防重）采集 JVM CPU/内存/磁盘 + 策略数 → 综合水位 → NORMAL/WARN(≥8成)/SCALE(≥9成)。
3. `AutoScaler` SPI（Noop 默认 / K8s HPA 预留，互斥装配）；SCALE 触发 scaleTo；阈值全部可配。
4. `/api/capacity/*`（写 ADMIN）；测试 + E2E。

## 验收
- 水位快照落库、latest/history 可查；SCALE 触发 AutoScaler；关闭自动伸缩仅记录。
- 非 ADMIN 刷新 403；定时采集自动执行。
- 生产以 K8s HPA 保证扩容零中断（适配层预留）。