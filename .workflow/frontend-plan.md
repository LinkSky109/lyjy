# P1 管理前端（web/）实施计划

> 目标：为 lhjypt 平台搭建完整管理前端 SPA，覆盖 P0/P1 全部模块，对接 core REST API（8081）。
> 技术栈：React 18 + TypeScript + Vite 5 + react-router-dom 6；零 UI 库（自研轻量组件 + 原生 CSS），保持本地可运行、无额外基础设施。

## 1. 架构
- `web/` 独立工程；开发态 Vite dev server（5173）通过 proxy 转发 `/api`、`/actuator` 到 `http://localhost:8081`，规避 CORS。
- 鉴权：后端以请求头模拟（`X-User-Role`/`X-User-Id`/`X-User-Group`）。前端登录页选择角色与用户，存 localStorage，`api` 层自动注入。
- 敏感操作：调用 `POST /api/auth/confirm-token` 获取一次性令牌，再带 `X-Confirm-Token` 执行。

## 2. 目录
```
web/
├─ src/
│  ├─ main.tsx / App.tsx / styles.css
│  ├─ api/http.ts            # fetch 封装 + Result 解包 + 错误
│  ├─ api/types.ts           # 业务类型
│  ├─ hooks/useApi.ts        # 数据加载 hook
│  ├─ auth/AuthContext.tsx   # 角色/用户上下文
│  ├─ components/            # Layout/Card/DataTable/Badge/Button/Field/AsyncState/JsonBlock/Modal
│  └─ pages/                 # 各模块页面
```

## 3. 页面与模块映射
| 页面 | 后端接口 |
|------|----------|
| 仪表盘 Dashboard | `/actuator/health`、`/api/monitor/metrics/latest`、`/api/monitor/alerts` |
| 交易 Trading | `/api/trade/orders`、`/api/trade/orders/{id}`、`/api/trade/platform/positions`、`/api/trade/platform/account` |
| 资金 Fund | `/api/fund/transaction`、`/api/fund/flows`、`/api/fund/reconcile` |
| 事前风控 Risk | `/api/risk/check`、`/api/risk/intercepts`、`/api/risk/blacklist` |
| 事中风控 Intraday | `/api/risk/intraday/*` |
| VaR | `/api/var/*` |
| 监控告警 Monitor | `/api/monitor/*` |
| 异常交易 Surveillance | `/api/surveillance/abnormal/*` |
| 组合 Portfolio | `/api/portfolio/*` |
| 日终清算 Settlement | `/api/settlement/*` |
| 多柜台 Gateway | `/api/gateway/*` |
| 策略安全 Strategy | `/api/strategy/*` |
| 性能降级 Degrade | `/api/degrade/*` |

## 4. 交付与验证
- `npm run build`（tsc + vite build）零错误。
- `npm run dev`（5173，proxy→8081）可登录并加载各模块数据。
- 生产：`npm run build` 产物 `web/dist` 可由任意静态服务器托管，或反向代理到 core。
