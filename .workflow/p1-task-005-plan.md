# TASK-P1-005 多柜台多通道执行计划

> 需求来源：`docs/p1-prd.md` §5.1
> 分支：`feature/p1-005-gateway-router`
> 目标：把单一 Mock 网关升级为可注册、可路由、可健康检查、可故障切换和可熔断的网关层。

## 1. 准出标准

- 支持注册 MOCK/CTP 网关并持久化到 `gateway_info`。
- 支持按账户、标的、订单类型和兜底规则匹配，优先级小者优先。
- 每 30 秒健康检查，连续 3 次失败标记 DOWN。
- 主网关不可用时自动切换到 fallback，且只重试 1 次。
- 同一网关 5 秒窗口错误率达到 50% 时熔断 30 秒。
- 路由规则 30 秒缓存，变更后立即刷新。
- 切换事件写入 Kafka `topic.gateway-switch`。
- 无可用网关返回 `GATEWAY_UNAVAILABLE`，不生成未知成交。
- 提供 PRD 要求的 8 个网关管理接口，并接入 ADMIN 权限。

## 2. 执行节点

### NODE-P1-005-001 数据模型与注册表

- 新增 `GatewayInfo`、`GatewayRouteRule` 实体和 Mapper。
- `orders` 增加 `gateway_id`，记录最终使用的网关。
- 新增增量迁移 `scripts/migration/p1_005_gateway_routing.sql`。
- 增加 schema 静态测试。

### NODE-P1-005-002 路由器与熔断

- 新增 `GatewayRegistry` 管理网关实例、状态和连接。
- 新增 `WindowedCircuitBreaker`，实现 5 秒窗口 50% 错误率熔断。
- 新增 `GatewayRouter`，实现规则缓存、路由选择、fallback 和无可用网关异常。
- 增加路由优先级、健康跳过、熔断 fallback 单元测试。

### NODE-P1-005-003 交易链路接入

- `TradeExecutionService` 改为通过 `GatewayRouter` 报单、撤单和查询。
- OMS 记录实际使用的 `gatewayId`。
- `TradeRecoveryService` 按订单网关查询原柜台。
- 保证既有 P1.5 E2E 不回归。

### NODE-P1-005-004 管理接口与事件

- 新增 `GatewayController`，实现 8 个接口。
- 路由和注册变更立即刷新缓存。
- 切换事件发 Kafka。
- ADMIN 权限和敏感操作确认口令生效。

### NODE-P1-005-005 测试与一致性

- 单元测试覆盖路由、切换、熔断、健康检查。
- 真实启动两个 Mock 网关，验证主网关断开后自动走 fallback。
- 更新测试报告、一致性报告和上下文。

## 3. 关键设计

### 路由匹配

规则排序：`priority ASC`，同级规则按 specificity 排序：

1. `accountId + symbolRegex + orderType`
2. `accountId + symbolRegex`
3. `accountId`
4. `symbolRegex + orderType`
5. 兜底规则

### 可用网关判定

```
status == READY
&& gateway.isAuthenticated()
&& breaker.allowRequest()
```

### 故障切换

```
primary.submitOrder
  -> success: 返回 gatewayId + orderRef
  -> failure/timeout:
       breaker.recordFailure(primary)
       发布 gateway-switch
       fallback.submitOrder（最多 1 次）
  -> fallback 失败:
       抛 GATEWAY_UNAVAILABLE
```

### 幂等边界

- 路由层不生成新订单号。
- OMS `orderId` 是全局幂等键。
- 切换只发生在报单尚未确认的场景；若已产生潜在成交，由对账和 OMS 幂等收敛。

## 4. API 清单

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| POST | `/api/gateway/register` | ADMIN | 注册或更新网关 |
| POST | `/api/gateway/{gatewayId}/connect` | ADMIN | 连接网关 |
| POST | `/api/gateway/{gatewayId}/disconnect` | ADMIN | 断开网关 |
| GET | `/api/gateway/list` | ADMIN/TRADER | 网关列表 |
| GET | `/api/gateway/{gatewayId}/health` | ADMIN | 健康详情 |
| POST | `/api/gateway/route` | ADMIN | 新建或更新路由规则 |
| GET | `/api/gateway/route/list` | ADMIN | 路由规则列表 |
| POST | `/api/gateway/{gatewayId}/switch` | ADMIN + 确认口令 | 手动切换账户流量 |

## 5. 测试计划

| 编号 | 场景 | 预期 |
|------|------|------|
| UT-RT-001 | 账户规则匹配 | 命中指定网关 |
| UT-RT-002 | 标的正则匹配 | 命中指定网关 |
| UT-RT-003 | 多规则优先级 | 小优先级优先 |
| UT-RT-004 | 主网关 DOWN | 选择 fallback |
| UT-RT-005 | fallback 也不可用 | GATEWAY_UNAVAILABLE |
| UT-RT-006 | 主网关报单异常 | fallback 接单并发布切换事件 |
| UT-RT-007 | 错误率达到 50% | 熔断 30 秒 |
| UT-RT-008 | 熔断窗口结束 | HALF_OPEN 探测恢复 |
| IT-RT-001 | 注册并连接两个 Mock 网关 | list/health 正常 |
| E2E-RT-001 | 主网关断开后下单 | 自动走 fallback 并成交 |
