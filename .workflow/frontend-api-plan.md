# 新版前端接口规划（Backend API Plan）

> 版本：v1.0 · 日期：2026-10-23
> 输入：`design/DESIGN-SPEC.md`、`design/AGENT-DEVELOPMENT.md`、当前 `web/src` 实现
> 范围：仅规划接口，不改后端实现；本文件作为下一阶段后端排期与联调依据
> 原则：**不破坏现有接口**；沿用 `Result<T>` 包装、`X-User-*` 身份头与租户上下文；新增接口优先只读、可灰度

---

## 1. 现状与缺口

新版前端已完成，以下能力受接口限制，需要后端补齐或扩展：

| 前端位置 | 当前做法 | 缺口 |
|---|---|---|
| 仪表盘 | 客户端用 `/api/trade/platform/account` 单账户聚合 | 无跨账户汇总、无当日盈亏、无统一状态（Kafka 无 health 组件） |
| 交易下单 | `/api/market/tick/{symbol}` 取行情 | 无合约元数据（乘数/最小变动/数量上限），名义价值只能按价格×数量估算 |
| 委托列表 | 仅 `/api/oms/orders/active` + 单笔查询 | 无分页历史、无“是否可撤/柜台确认状态”字段 |
| 撤单 | `DELETE /api/trade/orders/{id}` | MOCK 即时成交，无法构造可验证的待成交单；无测试专用挂单模式 |
| 组合 | `/api/portfolio/{id}` 的持仓/偏差 | 无净值历史、无策略贡献（持仓无 `strategyId`） |
| 风控 | `/api/risk/budget/*` | 无通用“风控规则”列表/开关/阈值接口；预算列表只返回启用项 |
| 资金 | 单账户 + 申请/审核/流水/对账 | 无账户列表；出金校验在前端，缺服务端强校验与幂等 |
| 监控 | `/api/monitor/alerts?status=` | 无 severity 过滤、无分页/计数；组件状态靠 `/actuator/health` |
| 列表通用 | 多次前端过滤 | 流水/成交/告警/组合缺少统一分页契约 |

---

## 2. 数据契约（新增接口统一遵守）

### 2.1 响应包装

```json
{ "code": 0, "message": "success", "data": {}, "timestamp": 1792738000000 }
```

- `code`: 0 成功；4xx/5xx 复用现有错误码语义
- 金额：`number`（保留 2–8 位小数）；百分比：`number`（0.0–100.0，1 位小数）
- 时间：ISO-8601 本地时间 `yyyy-MM-ddTHH:mm:ss`（与现状一致）
- 标识：字符串；分页见 §2.2

### 2.2 分页

请求：`page`（从 1 开始）、`size`（默认 20，最大 200）、`sort`
响应 `data`：

```json
{ "items": [], "total": 0, "page": 1, "size": 20 }
```

### 2.3 鉴权与租户

- 继续使用 `X-User-Role` / `X-User-Id` / `X-Tenant-Id`
- 读：对应业务角色；写：`ADMIN` 或业务角色；危险写（撤单/切换/再平衡/审核）保留一次性 `X-Confirm-Token`

### 2.4 幂等

- 所有创建型写接口支持 `Idempotency-Key`（或 body `idempotencyKey`），30 分钟内重复请求返回首次结果
- 返回体增加 `replayed: true|false`（可选字段，兼容旧前端）

---

## 3. P0：解除前端“不可信/不可验证”阻塞

### 3.1 仪表盘汇总

`GET /api/dashboard/summary` · **状态：已实现（2026-10-23）**

- 角色：`ADMIN`（其他角色返回本租户可见范围）
- 用途：一次请求渲染状态条 + 4 指标，避免前端拼装
- 响应：

```json
{
  "service": { "status": "UP", "version": "0.1.0" },
  "market": { "running": true, "subscribed": 3, "lastTickAt": "2026-10-23T10:00:00" },
  "components": { "postgres": "UP", "clickhouse": "UP", "redis": "UP", "kafka": "UP" },
  "equity": { "total": 999256.86, "available": 893824.38, "frozen": 14932.05, "margin": 90500.42, "marginRatio": 9.1 },
  "pnl": { "unrealized": 0.0, "realized": -460.20, "combined": -460.20, "basis": "CUMULATIVE" },
  "alerts": { "open": 2, "critical": 0 },
  "orders": { "active": 2, "filledToday": 1 },
  "asOf": "2026-10-23T10:00:00"
}
```

验收：单次调用覆盖首屏；Kafka 需新增 health indicator 或在 `components` 明确 `UNKNOWN`。

### 3.2 合约元数据

`GET /api/trade/instruments/{symbol}` · **状态：已实现（2026-10-23）**

- 角色：`TRADER` / `ADMIN` / `VIEWER`
- 响应：

```json
{
  "symbol": "rb2601", "name": "螺纹钢2601", "exchange": "SHFE",
  "priceTick": 1, "contractMultiplier": 10, "currency": "CNY",
  "minQty": 1, "maxQty": 200, "qtyStep": 1,
  "limitUp": 3900, "limitDown": 3300, "trading": true
}
```

- 用途：前端计算真实名义价值、校验数量上限/价格档位、显示交易状态
- 回退：未收录时返回 404 + 明确 message，前端显示“该合约未配置”

### 3.3 行情快照（统一）

`GET /api/market/quote/{symbol}` · **状态：已实现（2026-10-23）**

- 响应：最新价、昨收、开高低、成交量/额、五档买卖价量、`tickTime`、`staleMs`
- 现状复用 `TickData` 字段；新增 `staleMs` 用于前端提示“行情延迟”
- 目的：交易页与仪表盘共用同一契约，避免 `/api/market/tick` 与真实源口径分叉

### 3.4 委托历史与可撤状态 · **状态：已实现（2026-10-23）**

`GET /api/oms/orders?status=&symbol=&from=&to=&page=&size=`

- 分页返回订单；字段在现有基础上增加：
  - `cancelable: boolean`
  - `gatewayAckStatus: NONE | ACKED | REJECTED`
  - `exchangeOrderId`（无则 null）
- `GET /api/trade/orders/{id}` 同步增加上述字段
- 用途：前端只对 `cancelable=true` 显示撤单；明确 409 的原因分类

### 3.5 测试用“待成交”能力（仅非生产）· **状态：已实现（配置开关方式）**

实现方式：配置项 `lhjypt.trade.mock-fill-enabled=false`（环境变量 `LHJYPT_TRADE_MOCK_FILL=false`，默认 `true`）

- 作用：关闭 MOCK 自动成交后，订单保持 `NOT_TRADED/SUBMITTED`，供撤单链路 E2E 验证
- 默认 true，保持原有“即时成交”行为；生产无需该开关
- 相比新增 `sim` 接口，无需额外攻击面与权限分支

### 3.6 风险预算包含禁用项 · **状态：已实现（2026-10-23）**

`GET /api/risk/budget/list?includeDisabled=true`

- 默认保持现状（仅启用），避免破坏旧调用
- 前端传 `includeDisabled=true` 后返回禁用项，用于开关回显

### 3.7 服务组件

`GET /api/monitor/components` · **状态：已实现（2026-10-23）**

- 返回 `postgres/clickhouse/redis/kafka/market` 状态与延迟
- 替代前端直接解析 `/actuator/health` 的嵌套结构

---

## 4. P1：精做页面的数据完整性

### 4.1 组合净值历史

`GET /api/portfolio/{portfolioId}/nav?from=&to=&interval=1d`

```json
{ "portfolioId": "PF-xxxx", "baseCurrency": "CNY", "points": [
  { "ts": "2026-10-01", "nav": 1.0234, "equity": 1023400, "cash": 200000 }
] }
```

- 数据源建议：`clickhouse` 日快照表 或 新增 `portfolio_nav` 表（日终清算时写入）
- 无数据返回空数组 + `message`，前端显示空状态

### 4.2 策略贡献

`GET /api/portfolio/{portfolioId}/attribution?from=&to=`

```json
{ "items": [
  { "strategyId": "S-1", "pnl": 12500, "contribution": 3.2, "weight": 0.4 },
  { "strategyId": null, "pnl": -800, "contribution": -0.2, "weight": 0.1 }
] }
```

- 需要 `PortfolioPosition` 增加 `strategyId`（或按订单归属聚合）
- 前端按 `contribution` 排序展示条形贡献

### 4.3 风控规则（通用）· **部分实现（列表/开关已上线，阈值编辑待规则支持动态参数）**

- `GET /api/risk/rules` → 规则列表（ruleCode/name/enabled/threshold/action/updatedAt）
- `PUT /api/risk/rules/{ruleCode}` → 更新阈值与动作
- `POST /api/risk/rules/{ruleCode}/toggle` → `{ "enabled": true }`
- 角色：`RISK_OFFICER` / `ADMIN`；写操作审计
- 现状 `RiskController` 只有试算/黑名单/拦截日志，规则目前散落在代码常量；建议先做只读 + 开关，阈值编辑灰度

### 4.4 资金账户与划转强校验

- `GET /api/fund/accounts?page=&size=` → 账户列表（多账户/多币种）
- `POST /api/fund/transfer` → `{ from, to, amount, currency, idempotencyKey }`
  - 服务端校验可用资金、冻结、币种、账户状态；返回流水号
  - 出金仍走现有 `transaction` + 审核流，服务端必须复算可用资金，不信任前端
- `GET /api/fund/flows?accountId=&from=&to=&bizType=&page=&size=`
- `GET /api/fund/reconcile?accountId=&date=` → 查询历史对账，而非仅提交

### 4.5 交易分页

- `GET /api/trade/trades?accountId=&from=&to=&page=&size=`
- `GET /api/oms/orders/active?accountId=&strategyId=`（补过滤，保持原路径兼容）

### 4.6 组合与告警分页/过滤

- `GET /api/portfolio/list?page=&size=&status=`
- `GET /api/monitor/alerts?status=&severity=&from=&to=&page=&size=` + `GET /api/monitor/alerts/counts`
- `GET /api/monitor/alert-rule/list?page=&size=`（规则多时）

---

## 5. P2：体验与治理

| 接口 | 说明 |
|---|---|
| `GET /api/market/instruments?exchange=&keyword=` | 合约搜索/下拉，替代手输 symbol |
| `GET /api/dashboard/timeline?limit=20` | 最近委托/成交/告警混合时间线 |
| `POST /api/orders/batch-cancel` | 批量撤单（逐单结果，危险操作需令牌） |
| `GET /api/audit/events?page=&size=` | 前端操作审计查询 |
| `GET /api/notifications/unread` | 顶栏未读告警/通知计数 |
| `GET /api/export/{type}?format=csv` | 列表导出（异步任务 + 下载令牌） |

---

## 6. 前端页面映射与优先级

| 页面 | 依赖接口 | 优先级 |
|---|---|---|
| 仪表盘 | 3.1、3.7（已接入） | P0 ✅ |
| 交易下单 | 3.2、3.3（已接入）、3.4、3.5 | P0 |
| 事前风控 | 3.6、4.3 | P0/P1 |
| 资金管理 | 4.4 | P1 |
| 组合管理 | 4.1、4.2、4.6 | P1 |
| 监控告警 | 3.7、4.6 | P0/P1 |
| 其余 9 页 | 4.5、4.6、5 | P1/P2 |

---

## 7. 兼容与迁移策略

1. 新增接口只增不改；旧字段保留至少一个版本
2. 分页接口默认 `size=20`，旧的无分页路径保持不变
3. 金额字段保持 `number`；若未来改字符串，新增 `*Text` 字段而非替换
4. 测试专用接口必须 `@Profile("dev")` 或配置开关，默认关闭
5. 前端按“接口可用则启用、404/501 则回退空状态”实现，不做硬依赖

---

## 8. 验收标准

- 仪表盘 1 次请求完成首屏（P95 < 300ms）
- 下单名义价值与后端合约乘数一致；数量上限由服务端返回
- 撤单仅对 `cancelable=true` 可点；409 原因可读
- 组合净值/贡献有数据即渲染，无数据为空状态
- 风控规则开关/阈值变更即时生效并留审计
- 出金服务端强校验，前端绕过也无法超额
- 所有列表分页参数与返回结构一致

---

## 9. 暂不做

- 微服务拆分、CQRS/事件溯源改造
- 实时 WebSocket 推送（先用轮询，后续单独立项）
- 多活/跨机房
- 前端直连 ClickHouse 或新增 BFF 层

---

## 10. 建议落地顺序

1. **P0-a**：3.7 组件状态 + 3.1 仪表盘汇总（只读，低风险）· ✅ 已完成
2. **P0-b**：3.2 合约元数据 + 3.3 行情快照（交易页真实性）· ✅ 已完成
3. **P0-c**：3.4 委托历史/可撤 + 3.5 测试挂单（撤单闭环）· ✅ 已完成
4. **P1-a**：3.6 预算禁用项 + 4.3 风控规则 · ✅ 列表/开关完成
5. **P1-b**：4.4 资金账户/划转 + 4.5 分页
6. **P1-c**：4.1/4.2 组合净值与归因（依赖数据表）


---

## 11. 实施记录

### 2026-10-23 · P0-a 完成

- 新增 `GET /api/dashboard/summary`（`DashboardController` + `DashboardService`）
  - 聚合：服务状态、行情运行/订阅、PostgreSQL/ClickHouse/Redis/Kafka 组件、全部资金账户权益、累计盈亏、未确认告警、在途/当日成交委托
  - 盈亏口径：`unrealized + realized = combined`，`basis=CUMULATIVE`；后端暂无“当日盈亏”聚合，故不虚构 `today`
- 新增 `GET /api/monitor/components`
  - Kafka 未注册健康组件时返回 `UNKNOWN`
- 前端 `DashboardPage` 改为消费 `/api/dashboard/summary`，请求数 7 → 4；汇总失败展示可重试错误块
- 测试：`DashboardServiceTest`（2）+ `DashboardControllerTest`（1）
- 验证：`mvn -pl core verify` → 307 tests / 0 fail / 1 skip，JaCoCo 通过；`npm run build` 通过

### 2026-10-23 · P0-b 完成

- 新增 `GET /api/trade/instruments/{symbol}`（`InstrumentService` + `InstrumentProperties`）
  - 配置项：`lhjypt.instruments.contracts.*`（rb2410/hc2410/cu2410/au2410/IF2409）
  - 返回乘数、最小变动、数量上下限、币种、交易状态；未配置返回 `code=404`
- 新增 `GET /api/market/quote/{symbol}`
  - 复用 `TickData`，补 `staleMs`（按 recvTime/tickTime 计算）；无 tick 返回 `data=null`
- 前端交易页
  - 行情改走 `/api/market/quote/{symbol}`
  - 拉取合约元数据；名义价值 = 价格 × 数量 × 乘数（实测 rb2410：3500×1×10 = ¥35,000）
  - 数量校验使用后端 `minQty/maxQty`，未知合约回退 1/200
- 测试：`InstrumentServiceTest`（3）+ `MarketDataControllerTest`（2）
- 说明：合约元数据当前为 YAML 配置，后续可迁移 `instrument_info` 表

### 2026-10-23 · P0-c 完成

- `GET /api/oms/orders?status=&symbol=&page=&size=` 分页（`items/total/page/size`，size 上限 200）
- 订单响应新增 `cancelable`、`gatewayAckStatus`（ACKED/REJECTED/NONE）
- MOCK 网关新增 `lhjypt.trade.mock-fill-enabled`（默认 true）；关闭后订单保持待成交
- 前端交易页「委托」页签改为分页历史：状态筛选 + 上/下一页 + 总数；仅 `cancelable=true` 显示撤单按钮
- 端到端验证（关闭自动成交）：
  - 下单 → `SUBMITTED`，`cancelable=true`，`gatewayAckStatus=ACKED`
  - 撤单 → `CANCELED`，`cancelable=false`
  - UI 回执：`撤单成功 orderId=... status=CANCELED`
- 测试：`OrderManagementServicePagingTest`（3）+ `MockCTPGatewayHoldTest`（1）
- 验证后已将后端恢复默认（自动成交开启）

### 2026-10-23 · P1-a 完成（规则阈值编辑除外）

- `GET /api/risk/budget/list?includeDisabled=true`
  - 默认行为不变；前端风险页已改为始终展示禁用预算，移除本地临时保留逻辑
- `GET /api/risk/rules`
  - 返回 5 条事前规则：BLACKLIST/FUND_CHECK/FREQ_LIMIT/POSITION_LIMIT/SELF_TRADE
  - 字段：ruleCode/ruleName/enabled/configured/thresholdParams/action/updatedAt
- `POST /api/risk/rules/{ruleCode}/toggle?enabled=`
  - 写 `risk_rule_config` 并更新运行时覆盖，下一次下单立即生效
  - 权限：RISK_OFFICER / ADMIN；TRADER 实测 403
- 前端风险页新增「事前风控规则」表：开关即时生效 + Toast/Notice 回执
- 未开放阈值编辑：现有规则阈值来自 `@Value` 硬编码，需先为 5 条规则实现参数注入，避免提供“看似生效实则无效”的编辑入口
- 测试：`RiskRuleConfigServiceTest`（4）+ 预算 includeDisabled（1）
