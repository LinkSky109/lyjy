# OBS-017 熔断告警通知联动 · 测试报告

> 分支：feature/obs-016-circuit-breaker（承接） · 日期：2026-10-08 · 状态：VERIFIED

## 1. 交付内容
- `StrategyCircuitBreakerService` 熔断触发时新增**告警落库 + 外部渠道通知**：
  - 落库 `alert_event`：`rule_id=strategy.circuit`、`metric_key=strategy:<id>`、`severity=CRITICAL`、`status=OPEN`、message 含峰值浮盈率/当前/回撤/阈值
  - 复用 `AlertNotifier`（钉钉/企业微信/webhook）投递；通知失败仅记日志，不影响停止动作
- 与既有监控告警（AlertEvaluator → 落库 → 通知）同构，无需新增表/通道。

## 2. 实测（真实 E2E）
| 步骤 | 结果 |
|------|------|
| 运行策略建仓 + 配置阈值 1e-6 | RUNNING |
| 构造浮亏 + 峰值 | — |
| 定时扫描 | 第 3 轮熔断 **STOPPED** |
| 告警落库 | `EVT-c08a290f · strategy.circuit · strategy:SIG-TRADE · CRITICAL · OPEN · "[CRITICAL] 策略回撤熔断 SIG-TRADE - 峰值浮盈率 0.10 当前 -0.1169..."` |

## 3. 质量门禁
| 项 | 结果 |
|----|------|
| `StrategyCircuitBreakerServiceTest` | 3/3（触发场景新增告警落库 + 通知断言） |
| 全量 `mvn verify` | **283/283**（0 跳过，JaCoCo 通过） |

## 4. 说明
- 通知渠道沿用现有 `lhjypt.notify.*` 配置；未配置时实现记日志（不阻断）。
- 后续可把值守拉起失败、优化完成等策略事件纳入同一告警通道。

## 5. 结论
熔断不再"静默停止"：触发即告警落库并推送外部渠道，单人可及时获知策略风险处置。