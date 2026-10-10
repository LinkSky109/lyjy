# 观测栈接入（P4-003：Prometheus + Grafana）

> 单机自用的可观测性：把后端 `/actuator/prometheus` 的指标真正接进 Prometheus，并在 Grafana 预置数据源与看板。
> 一键启停：`./scripts/obs.sh up|down|status`。

## 组成

| 组件 | 镜像 | 宿主机端口 | 说明 |
|------|------|-----------|------|
| Prometheus | `prom/prometheus:v2.54.1` | 9090 | 抓取后端指标，保留 15d；`job=lhjypt` |
| Grafana | `grafana/grafana:11.2.0` | 3000 | 预置 Prometheus 数据源 + `lhjypt 平台总览` 看板 |

配置位于 `config/observability/`：`docker-compose.yaml`、`prometheus.yml.tmpl`（脚本生成到 `.generated/`）、`grafana/provisioning/**`、`grafana/dashboards/lhjypt-overview.json`。

## 使用

```bash
./scripts/obs.sh up       # 生成抓取配置并启动（默认抓 host.lima.internal:8081）
./scripts/obs.sh status   # 容器状态 + Prometheus 抓取目标健康
./scripts/obs.sh down     # 停止（保留数据卷）

# 抓取目标可配（Docker Desktop 用 host.docker.internal）：
LHJYPT_SCRAPE_TARGET=host.docker.internal:8081 ./scripts/obs.sh up
```

- Grafana：http://localhost:3000 ，`admin / lhjypt`（可用 `GF_ADMIN_PASSWORD` 覆盖）；
  首页默认即 `lhjypt 平台总览`（HTTP 速率/P95、回测运行与耗时、行情刷新、熔断、容量水位、JVM Heap）。
- Prometheus：http://localhost:9090 （Targets 里 `lhjypt` 应为 `up`）。

## 看板指标

| 面板 | PromQL（节选） |
|------|----------------|
| HTTP 请求速率 | `sum by (uri) (rate(http_server_requests_seconds_count{application="lhjypt-core"}[5m]))` |
| HTTP P95 延迟 | `histogram_quantile(0.95, sum by (le,uri) (rate(http_server_requests_seconds_bucket[5m])))` |
| 回测运行 / 耗时 | `increase(lhjypt_backtest_runs_total[1h])`、`histogram_quantile(0.95, ... lhjypt_backtest_duration_seconds_bucket ...)` |
| 行情刷新 | `increase(lhjypt_market_refresh_total[1h])`、`increase(lhjypt_market_refresh_inserted_total[1h])` |
| 熔断触发 | `increase(lhjypt_circuit_trips_total[1h])` |
| 容量水位 | `lhjypt_capacity_load_pct` |
| JVM Heap | `sum(jvm_memory_used_bytes{application="lhjypt-core", area="heap"})` |

指标定义与扩展方式见 [metrics.md](metrics.md)。

## 实测（2026-10-10）

- `obs.sh up` 启动 `lhjypt-prometheus`(9090) / `lhjypt-grafana`(3000)。
- Prometheus 目标 `lhjypt → http://host.lima.internal:8081/actuator/prometheus` **up**；
  抓取到 `lhjypt_*`（3）、`jvm_memory_used_bytes`（8）、`http_server_requests_*`（3）、`hikaricp_connections_*`（2）等系列。
- Grafana 已 provision：数据源 `Prometheus`(uid `lhjypt-prom`)、看板 `lhjypt 平台总览`(uid `lhjypt-overview`)。

## 边界

- 观测栈为**可选**、独立 compose（项目名 `observability`），与主中间件互不影响；`down` 不清数据卷。
- 本地单用户：Grafana 默认口令仅为本地便利，如需对外暴露务必改密/禁用匿名（默认已 `GF_USERS_ALLOW_SIGN_UP=false`）。
