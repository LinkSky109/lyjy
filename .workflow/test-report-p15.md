# P1.5 测试报告

> 任务ID: TASK-20261005-002
> 报告时间: 2026-10-05 19:52:16
> 当前测试状态: TEST_BLOCKED
> 阻塞原因: 本机 Docker daemon 不可用，无法执行 PostgreSQL Testcontainers 和完整中间件 E2E

---

## 1. 执行概况

| 测试层 | 计划 | 执行 | 通过 | 跳过 | 状态 |
|--------|------|------|------|------|------|
| Java 单元测试 | 全部 | 97 | 96 | 1 | 通过 |
| Java Testcontainers schema | 1 | 1 | 0 | 1 | 环境阻塞 |
| Python 单元测试 | 全部 | 23 | 23 | 0 | 通过 |
| Java 覆盖率门禁 | 2 条规则 | 2 | 2 | 0 | 通过 |
| Python 覆盖率门禁 | 1 条规则 | 1 | 1 | 0 | 通过 |
| 容器化 E2E | 1 条主链路 | 0 | 0 | 0 | 环境阻塞 |

---

## 2. Java 测试结果

命令：

```bash
mvn -B clean verify
```

结果：

- `BUILD SUCCESS`
- 测试总数：97
- 失败：0
- 错误：0
- 跳过：1
- 跳过用例：`SchemaMigrationIntegrationTest`
- 跳过原因：本机无可用 Docker 环境

覆盖的功能：

- 统一订单编排和风控拒绝。
- 幂等重放和资金冻结补偿。
- MOCK/LIVE 运行模式快速失败。
- OMS 回报状态收敛。
- 成交后持仓、资金和成交记录更新。
- 持仓对账和订单恢复。
- 权限拦截、traceId 传播。
- 资金守恒和手续费口径。

---

## 3. Java 覆盖率

命令：

```bash
mvn -B clean verify
```

结果：

| 范围 | 门槛 | 实测 | 结果 |
|------|------|------|------|
| 新增交易包行覆盖率 | ≥ 80% | 81.95% | 通过 |
| 全量行覆盖率 ratchet | ≥ 33% | 34.03% | 通过 |
| 全量分支覆盖率 | 本期仅记录 | 13.09% | 记录 |

JaCoCo 输出：

- `core/target/site/jacoco/index.html`
- `core/target/site/jacoco/jacoco.csv`

---

## 4. Python 测试结果

命令：

```bash
uv run --python 3.11 --with pytest --with pytest-cov \
  python -m pytest tests/ --cov=lhjypt --cov-report=term --cov-fail-under=80 -q
```

结果：

- 测试总数：23
- 通过：23
- 失败：0
- 覆盖率：85.27%
- 覆盖率门槛：80%
- 结果：通过

覆盖的功能：

- 指标计算。
- K线合成。
- 策略生命周期。
- 回测数据与订单 API。
- 实盘 SDK 下单、撤单、持仓和账户查询。
- 示例策略。

---

## 5. 未执行测试

### 5.1 PostgreSQL schema 集成测试

- 测试类：`SchemaMigrationIntegrationTest`
- 计划动作：
  1. 启动 PostgreSQL 15 Testcontainer。
  2. 执行 `scripts/init-db.sql`。
  3. 执行增量迁移脚本。
  4. 校验新增列和表。
- 阻塞：本机无 `/var/run/docker.sock`，Testcontainers 自动跳过。

### 5.2 完整 E2E

计划链路：

```
Python SDK 下单
  -> API 权限拦截
  -> 真实风控
  -> OMS
  -> 资金冻结
  -> Mock 网关
  -> 成交回报
  -> 持仓和资金更新
  -> 持仓对账
  -> 重启恢复
```

阻塞：

- Docker daemon 未运行。
- PostgreSQL、ClickHouse、Kafka、Redis 未启动。

---

## 6. 当前结论

- 单元测试和静态质量门禁全部通过。
- 新代码覆盖率目标已满足。
- 数据 schema 与 E2E 尚未完成真实容器验证。
- 测试阶段不能流转 `TEST_PASSED`，当前状态为 `TEST_BLOCKED`。

### 解除阻塞动作

1. 启动 Docker 或 Lima Docker 实例。
2. 执行 `.env.example` 初始化。
3. 启动 PostgreSQL、ClickHouse、Kafka、Redis。
4. 重跑 `mvn clean verify`，使 Testcontainers 用例真实执行。
5. 执行 Python SDK 到核心服务的 E2E 冒烟。
6. 更新本报告并流转 `TEST_PASSED`。
