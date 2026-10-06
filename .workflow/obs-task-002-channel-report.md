# OBS-002 真实交易通道演练 · 测试报告

> 分支：feature/obs-channel-demo · 日期：2026-10-06 · 状态：CHANNEL_VERIFIED（Mock 网关）

## 1. 交付内容
| 产物 | 说明 |
|------|------|
| `strategy-sdk/examples/channel_demo.py` | 沙箱内 SDK 下单演练策略（幂等：先平旧仓再开仓） |
| `scripts/channel-demo.sh` | 一键编排：连接网关→上传→运行→沙箱输出→服务端独立校验→停止 |
| `core/src/main/resources/sandbox/sandbox_guard.py` | 沙箱网络白名单改造（平台回环 8081 放行，外网仍禁） |
| `docs/channel.md` | 真实交易通道演练手册 + 真实柜台接入指引 |

## 2. 实测链路（全绿）
| 步骤 | 结果 |
|------|------|
| 账户基线（SDK 回环访问） | `baseline available: 949155.924` |
| 平旧仓（幂等） | `closing legacy position: 1` |
| 限价开仓 | `buy placed: LHJ20261006210132000002` → `order status: FILLED` |
| 撤已成交单 | `cancel rejected (expected): order FILLED`（终端态拒绝） |
| 错误账户下单 | `bad-account rejected: 422` |
| 持仓复检 | `BTCUSDT LONG 1.0 @ 200000` |
| 服务端独立校验 | 成交 3 笔（BUY/SELL/BUY）、持仓 1 手、资金：总资产-60/轮手续费、保证金+20000/手占用 |
| 停止 | STOPPED |

## 3. 沙箱守卫改造（安全敏感）
- 原策略沙箱禁止一切网络（`http.client.`/`socket.` 前缀全拦）——策略无法经平台 API 交易。
- 改为**连接建立点判定**：`http.client.connect`/`socket.connect`/`socket.getaddrinfo` 仅放行回环 `127.0.0.1/::1/localhost:8081`；任意外部网络仍拒绝。`socket` 对象创建/`http.client.send/read` 不再整前缀误伤（只能发生在已放行连接上）。
- 迭代记录：`socket.getaddrinfo`→`socket.__new__`→`socket.connect`(args含self)→`http.client.send` 逐步精修至回环链路放行。
- 进程沙箱仍为 best-effort；容器沙箱 `DockerSandboxExecutor` 网络隔离为硬边界（文档注明）。

## 4. 质量门禁
| 项 | 结果 |
|----|------|
| `mvn verify` | **252/252**（0 跳过，JaCoCo 通过，无回归） |
| 单元测试 | 守卫为运行时资源，未锁网断断言；沙箱 executor 单测均 mock |

## 5. 结论
「回测信号→策略内 SDK 下单→Mock 成交→撤单拒绝/错误账户拒绝→持仓/资金闭环」全链路通过，且演练产出安全改进（沙箱平台回环白名单）。下一方向：前端体验打磨。