# P4-005 观测栈告警规则 · 任务报告

- 任务：P4-005 观测栈告警规则（Prometheus alerting rules）
- 依据：`delivery-report-p4.md` §6「观测栈告警规则按需补充」
- 状态：DONE

## 背景（含 P4-004 结论）

原计划 P4-004「备用行情源再增」。实测本机出网受限：gateio / gateio-futures / binance-vision 可达，
OKX / Bybit / Coinbase / Kraken / MEXC / HTX / CoinGecko / api.binance.com 等**全部连接超时**（DNS 可解析、TCP 不通）。
无法对新增源做真实 E2E，按「不交付未验证集成」原则将 P4-004 置 **BLOCKED**，改交付完全可验证的告警规则。

## 改动（纯新增，不改应用代码）

| 文件 | 说明 |
|------|------|
| `config/observability/prometheus.rules.yml` | 6 条告警规则（见下表） |
| `config/observability/prometheus.yml.tmpl` | 增 `rule_files: /etc/prometheus/rules/*.yml` |
| `config/observability/docker-compose.yaml` | 挂载 rules 到 `/etc/prometheus/rules/` |
| `docs/observability.md` | 告警规则章节 + 演练记录 + bind-mount 教训 |

| 告警 | 表达式 | for | 级别 |
|------|--------|-----|------|
| LhjyptBackendDown | `up{job="lhjypt"} == 0` | 1m | critical |
| LhjyptHttpP95High | HTTP P95 > 1s | 5m | warning |
| LhjyptMarketRefreshErrors | `increase(lhjypt_market_refresh_total{result="error"}[10m]) > 0` | 0m | warning |
| LhjyptCircuitTripped | `increase(lhjypt_circuit_trips_total[10m]) > 0` | 0m | critical |
| LhjyptCapacityHigh | `lhjypt_capacity_load_pct > 85` | 5m | warning |
| LhjyptJvmHeapHigh | Heap 使用率 > 90% | 5m | warning |

## 验证

- `promtool check rules`：**SUCCESS: 6 rules found**。
- `/api/v1/rules`：6 条 `type=alerting` `health=ok`。
- 受控触发：抓取目标临时指向 `127.0.0.1:9` 并重载 → `LhjyptBackendDown` 由 `inactive` → **`firing`（critical）**；
  恢复目标 (`host.lima.internal:8081`, up=1) 后旧死实例序列 staleness 过期，规则回到 **`inactive`**，`/api/v1/alerts` 0 条。

## 教训
- 单文件 bind mount 不能用 BSD `sed -i` 就改（换 inode 会让容器内文件"消失"，reload 报 no such file）；改用写入同一 inode（`sed > file` / `obs.sh`）或改环境变量 + 重建。
- 切换抓取目标推荐 `LHJYPT_SCRAPE_TARGET=... ./scripts/obs.sh up`。

## 边界
- 仅规则评估，未接 Alertmanager（外发渠道可选）；业务侧 `AlertNotifier` 仍为运行期兜底通知。
- 不改应用代码、无 DB 变更。
