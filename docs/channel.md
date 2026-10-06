# 真实交易通道演练（Mock 网关）

> 目标：验证「策略在沙箱内产生信号 → SDK 经平台 API 下单 → 网关成交回报 → 持仓/资金更新 → 异常路径」完整通道。
> 当前成交网关为 **Mock（本地高流动性，限价单自动全部成交）**；接入真实柜台时替换 `TradingGateway` 适配层（见 `docs/architecture.md`）。

## 一键演练

```bash
./scripts/dev.sh up && ./scripts/channel-demo.sh
```

`channel-demo.sh` 自动完成：
1. 账户资金基线（DEFAULT-001）
2. 连接交易网关（`userId=trader-1`，Mock 自动登录）
3. 上传演练策略 `CH-DEMO`（源码 = `strategy-sdk/examples/channel_demo.py`，加密 AES-256-GCM）
4. 沙箱运行策略 → 策略内经 `LiveOrderAPI` 下单
5. 沙箱输出收集（outputTail）+ 服务端独立校验（成交/持仓/资金）

## 通道内的关键行为

| 步骤 | 期望输出 | 说明 |
|------|----------|------|
| 账户基线 | `baseline available: <可用资金>` | SDK 回环访问平台 API 成功 |
| 限价开仓 | `order status: FILLED` | Mock 网关自动成交，回报状态收敛 |
| 撤已成交单 | `cancel rejected (expected): order FILLED` | 终端态订单拒绝撤销（安全路径） |
| 错误账户下单 | `bad-account rejected: 422` | 非法账户被下单校验拒绝 |
| 持仓复检 | `final BTCUSDT position qty: 1.0` | 平台账本持仓与成交一致 |
| 服务端校验 | 成交记录/持仓/保证金变化 | 总资产-手续费、保证金+占用一致 |

脚本**幂等**：策略先平旧仓再开仓，每轮净持仓保持 1 手。

## 沙箱网络白名单（本次演练新增能力）

策略沙箱默认全禁外网（防数据外泄）。演练打通「策略经平台 API 读写/下单」通道：
- `sandbox_guard.py` 网络判定收敛到**连接建立事件**（`http.client.connect` / `socket.connect` / `socket.getaddrinfo`）；
- **仅放行回环 `127.0.0.1/::1/localhost:8081`**（平台本地 API），任意外部网络仍被拒绝；
- 进程级沙箱为 best-effort 边界；容器沙箱（`DockerSandboxExecutor`）网络隔离为硬边界。

## 扩展：接入真实柜台

1. 实现/替换 `TradingGateway` 适配器（参考 `CTPGatewayAdapter` 预留）；
2. `TradeController.connect` 传入柜台配置（broker/appId/user）；
3. 网关登录成功后下单链路无需改动（统一走 `TradeApplicationService`）。

## 安全边界提示

- 策略内可访问的平台 API 范围 = SDK 提供的只读行情/账户/持仓 + 下单/撤单，均携带 `X-User-Role: TRADER` 身份；
- 如需更严格隔离（策略不可触达资金查询），可缩小 `PLATFORM_API_*` 白名单或改用容器沙箱。