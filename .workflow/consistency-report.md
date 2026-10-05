# 发布前一致性巡检报告

> 任务：TASK-20261002-001 企业级量化交易平台（lhjypt）
> 巡检日期：2026-10-02
> 阶段：GLOBAL_IN_CONSISTENCY → GLOBAL_DONE

## 一、四维度巡检结果

| 维度 | 巡检内容 | 结果 |
|------|----------|------|
| 代码一致性 | 编译、规范、依赖版本、死代码、License | ✅ 通过 |
| 环境一致性 | 容器、配置、端口、镜像版本 | ✅ 通过 |
| 数据一致性 | 库表结构、基线数据、对账、字典 | ✅ 通过 |
| 文档一致性 | 接口契约、启动提示、SDK默认地址 | ✅ 通过（已修正） |

## 二、代码一致性

| 指标 | 实测 |
|------|------|
| Java 源文件 | 81 |
| REST 控制器 | 7 |
| REST 端点 | 47（+控制器基路径，覆盖测试计划 48 组用例） |
| Mapper | 8 |
| 编译 | `mvn clean compile` BUILD SUCCESS，无阻断 |
| 死代码 | 已清理（BUG-001/002/003） |
| Python SDK | 21 模块，14/14 测试通过 |

## 三、环境一致性

| 项 | 实测 |
|----|------|
| JDK | 17.0.20（openjdk@17） |
| Maven | 3.10.0（JAVA_HOME 显式指向 JDK17） |
| 容器运行时 | Lima 实例 tuna-docker |
| 中间件 | postgres / clickhouse / kafka / redis / zookeeper 全部 Up |
| 服务端口 | 8081（8080 被他项目占用，已规避并全局同步） |
| 服务健康 | /api/trade/status 返回 HTTP 200 |

## 四、数据一致性

| 存储 | 结果 |
|------|------|
| PostgreSQL | public 库 10 张表（orders/fund_account/position/strategy/order_idempotent/risk_rule_config/risk_intercept_log/cash_transaction/fund_flow/fund_reconciliation） |
| ClickHouse | lhjypt 库 3 张表（tick_data/bar_data/market_subscription） |
| 基线数据 | DEFAULT-001 账户就位，初始 1,000,000 |
| 资金对账 | 系统 vs 网关余额一致，差异 0，MATCHED |

## 五、文档一致性（本轮修正）

| 问题 | 修正 |
|------|------|
| 启动成功横幅显示 API 端口 8080，与实际 8081 不符 | 改为 8081 |
| Python LiveClient 默认 base_url 为 8080 | 改为 8081 |

## 六、巡检结论

- 四个维度全部通过，无阻断性不一致；发现的文档/提示不一致已当场修正并重新编译验证。
- 全链路（开发 → 测试 → 一致性）准出标准均已满足。

### 流转结论

一致性校验通过，全局状态由 **GLOBAL_IN_CONSISTENCY** 流转至 **GLOBAL_DONE**，P0 一期可交付。
