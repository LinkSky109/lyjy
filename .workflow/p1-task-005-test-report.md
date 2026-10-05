# TASK-P1-005 多柜台多通道测试报告

> 分支：`feature/p1-005-gateway-router`
> 日期：2026-10-05
> 状态：TEST_PASSED

## 1. 自动化测试

| 测试层 | 结果 |
|--------|------|
| Java 单元与集成测试 | 111/111 通过，0 跳过 |
| Testcontainers PostgreSQL schema | 通过 |
| Python 单元测试 | 23/23 通过 |
| Python 覆盖率 | 85.27%，门槛 80% |
| Java 交易包覆盖率 | 门槛通过 |

命令：

```bash
DOCKER_HOST=unix:///Users/link/.lima/tuna-docker/sock/docker.sock \
TESTCONTAINERS_RYUK_DISABLED=true \
mvn -B clean verify -Dapi.version=1.44

uv run --python 3.11 --with pytest --with pytest-cov \
  python -m pytest tests/ --cov=lhjypt --cov-report=term --cov-fail-under=80 -q
```

## 2. 双网关 E2E

环境：

- Java 核心服务：8081
- PostgreSQL、ClickHouse、Kafka、Redis：healthy
- 网关：`mock-primary`、`mock-fallback`
- 路由规则：`DEFAULT-001 -> mock-primary -> mock-fallback`

主网关报单：

| 项目 | 结果 |
|------|------|
| 订单 | `LHJ20261005203755000001` |
| gatewayId | `mock-primary` |
| 状态 | FILLED |
| 成交数量 | 1 |
| fallbackUsed | false |

断开主网关后：

| 项目 | 结果 |
|------|------|
| 订单 | `LHJ20261005203755000002` |
| gatewayId | `mock-fallback` |
| 状态 | FILLED |
| 成交数量 | 1 |
| fallbackUsed | true |
| switchedFrom | `mock-primary` |
| 主网关状态 | DOWN |
| 备用网关状态 | READY |

Kafka 切换事件：

```json
{
  "accountId": "DEFAULT-001",
  "strategyId": "S-GW-E2E-5",
  "symbol": "cu2410",
  "fromGateway": "mock-primary",
  "toGateway": "mock-fallback",
  "reason": "primary-unavailable"
}
```

## 3. 新增能力

- 网关注册、连接、断开和持久化。
- 按账户、标的、订单类型和兜底规则路由。
- 30 秒健康检查和连续 3 次失败标记 DOWN。
- 5 秒窗口 50% 错误率熔断，30 秒后半开探测。
- 主网关不可用时自动路由 fallback。
- Kafka `topic.gateway-switch` 切换事件。
- 8 个网关管理接口和 ADMIN 权限。
- `orders.gateway_id` 记录实际网关。

## 4. 缺陷闭环

| 缺陷 | 影响 | 修复 |
|------|------|------|
| Spring 无法选择 `GatewayRegistry` 构造器 | 服务启动失败 | 标注 `@Autowired` 主构造器 |
| 回报去重使用固定 `ACTIVE` 命名空间 | fallback 回报被误判重复 | 按实际 `gatewayId` 隔离 |
| `trade_record.trade_id` 全局唯一 | 不同网关同编号成交被跳过 | 改为 `gatewayId + sessionId + tradeId` 联合唯一 |

## 5. 结论

- 网关注册、路由、故障切换、熔断、健康检查和事件发布全部通过。
- P1-005 满足准出标准，状态流转 `DEV_DONE -> TEST_PASSED`。
