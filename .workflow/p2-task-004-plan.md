# TASK-P2-004 绩效归因

> 需求：`requirements-supplement.md` 模块二 §2.5
> 依赖：P1-007/P1-008

## 节点
1. `attribution_report` 表（init-p2.sql + 迁移 p2_004）。
2. `AttributionService`：Brinson（分配/选股/交互 + 恒等式）、因子暴露（Σ 权重×暴露）、执行归因（IS/滑点/手续费 bps）。
3. `/api/attribution/*`（brinson/factor/execution + report 列表/详情）；写权限 COMPLIANCE/ADMIN。
4. 测试 + E2E。

## 验收
- Brinson 恒等式成立；因子暴露聚合正确；执行 IS/滑点 bps 数值正确。
- 结果按类型+作用域+周期落库可查；TRADER 计算 403。