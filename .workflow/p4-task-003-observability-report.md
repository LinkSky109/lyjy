# P4-003 观测栈接入 · 任务报告

- 任务：P4-003 观测栈接入（本机 Prometheus + Grafana）
- 依据：`delivery-report-p3.md` §6「观测栈可选接入」
- 状态：DONE

## 改动（纯新增，不改应用代码）

| 文件 | 说明 |
|------|------|
| `config/observability/docker-compose.yaml` | Prometheus v2.54.1(9090) + Grafana 11.2.0(3000)，独立项目 `observability`，`restart: unless-stopped` |
| `config/observability/prometheus.yml.tmpl` | 抓取模板（`job=lhjypt`，`/actuator/prometheus`），`__TARGET__` 由脚本替换 |
| `config/observability/grafana/provisioning/**` | 预置 Prometheus 数据源(uid `lhjypt-prom`) + 看板 provider |
| `config/observability/grafana/dashboards/lhjypt-overview.json` | 总览看板（9 面板：HTTP 速率/P95、回测运行与耗时、行情刷新、熔断、容量、JVM Heap） |
| `scripts/obs.sh` | `up|down|status`；生成 `.generated/prometheus.yml`；目标可配 `LHJYPT_SCRAPE_TARGET` |
| `docs/observability.md` | 使用与指标说明；`docs/metrics.md` 指向一键栈 |
| `.gitignore` | 忽略 `config/observability/.generated/` |

## 验证（真实容器）

- `./scripts/obs.sh up` 拉起 `lhjypt-prometheus`(9090) / `lhjypt-grafana`(3000)。
- Prometheus 目标 `lhjypt → http://host.lima.internal:8081/actuator/prometheus` **health=up**（`up{job="lhjypt"}=1`）。
- 抓取到指标系列：`lhjypt_*`(3)、`jvm_memory_used_bytes`(8)、`http_server_requests_seconds_count`(3)、`hikaricp_connections_max`(2)，含 `lhjypt_capacity_load_pct` gauge。
- Grafana API：数据源 `Prometheus`(uid `lhjypt-prom`, url `http://lhjypt-prometheus:9090`)；看板搜索命中 `lhjypt 平台总览`(uid `lhjypt-overview`)。
- `obs.sh status` 打印容器状态与目标健康。

## 边界
- 观测栈可选、独立 compose，`down` 保留数据卷；不影响主中间件与后端。
- 本地单用户默认口令 `admin/lhjypt`，已禁用注册；对外暴露需改密。
- 抓取目标默认 `host.lima.internal`（Lima）；Docker Desktop 用 `LHJYPT_SCRAPE_TARGET=host.docker.internal:8081`。
