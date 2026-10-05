# P1.5 测试报告

> 任务ID: TASK-20261005-002
> 报告日期: 2026-10-05
> 最终状态: TEST_PASSED

---

## 1. 执行概况

| 测试层 | 执行 | 通过 | 失败 | 跳过 | 状态 |
|--------|------|------|------|------|------|
| Java 单元与集成测试 | 97 | 97 | 0 | 0 | 通过 |
| Testcontainers PostgreSQL schema | 1 | 1 | 0 | 0 | 通过 |
| Python 单元测试 | 23 | 23 | 0 | 0 | 通过 |
| Java 覆盖率门禁 | 2 条规则 | 2 | 0 | 0 | 通过 |
| Python 覆盖率门禁 | 1 条规则 | 1 | 0 | 0 | 通过 |
| 容器化 E2E | 5 个场景 | 5 | 0 | 0 | 通过 |
| 重启恢复与对账 | 1 个场景 | 1 | 0 | 0 | 通过 |

---

## 2. Java 测试

命令：

```bash
DOCKER_HOST=unix:///Users/link/.lima/tuna-docker/sock/docker.sock \
TESTCONTAINERS_RYUK_DISABLED=true \
mvn -B clean verify -Dapi.version=1.44
```

结果：

- `BUILD SUCCESS`
- 测试数：97
- 失败：0
- 错误：0
- 跳过：0
- Spring Boot 可执行 Jar 重新打包成功。

覆盖内容：

- 统一订单编排、真实风控、幂等和资金冻结。
- MOCK/LIVE 运行模式。
- OMS 状态机与柜台回报。
- 成交、持仓和资金联动。
- 持仓对账、活跃订单恢复。
- 角色权限和 traceId。
- PostgreSQL 全量 schema 和增量迁移。
- Python 实盘 SDK 契约。

---

## 3. Java 覆盖率

| 范围 | 门槛 | 实测 | 结果 |
|------|------|------|------|
| 新增交易包行覆盖率 | ≥ 80% | 81.95% | 通过 |
| 全量行覆盖率 ratchet | ≥ 33% | 34.03% | 通过 |

报告路径：

- `core/target/site/jacoco/index.html`
- `core/target/site/jacoco/jacoco.csv`

---

## 4. Python 测试

命令：

```bash
uv run --python 3.11 --with pytest --with pytest-cov \
  python -m pytest tests/ --cov=lhjypt --cov-report=term --cov-fail-under=80 -q
```

结果：

- 测试数：23
- 通过：23
- 失败：0
- 覆盖率：85.27%
- 门槛：80%
- 结果：通过

---

## 5. 端到端冒烟

执行环境：

- Java 核心服务端口 8081。
- PostgreSQL 15、ClickHouse 23.8、Kafka 3.7、Redis 7 全部健康。
- MockCTP 模式。

执行链路：

```
连接 Mock 柜台
  -> POST /api/trade/orders
  -> 真实风控
  -> OMS 建单
  -> 资金冻结
  -> Mock 成交
  -> 回报处理
  -> 持仓和资金更新
  -> 平台查询
  -> 持仓对账
```

实测结果：

| 项目 | 结果 |
|------|------|
| 订单状态 | FILLED |
| 成交数量 | 2 |
| 持仓数量 | 2 |
| 保证金 | 700.0 |
| 可用资金 | 999297.9 |
| 总资产 | 999997.9 |
| 幂等重复请求 | 返回原订单，不重复记账 |
| 持仓对账 | MATCHED |

---

## 6. 重启恢复

执行动作：

1. 完成开仓并写入 PG。
2. 停止核心服务。
3. 重新启动核心服务。
4. 查询订单、持仓和账户。
5. 重新连接 Mock 柜台并执行持仓对账。

实测结果：

| 项目 | 结果 |
|------|------|
| 重启后订单状态 | FILLED |
| 重启后持仓 | 2 |
| 重启后可用资金 | 999297.9 |
| Mock 重启后对账 | MISMATCHED |
| 识别差异 | 2 |

结论：

- PG 账本恢复成功。
- Mock 内存状态重置可被对账机制准确识别，不会静默掩盖差异。

---

## 7. 测试结论

- 单元、集成、E2E 和覆盖率门禁全部通过。
- 无未闭环缺陷。
- 测试阶段满足准出标准。
- 状态流转 `TEST_PASSED`。
