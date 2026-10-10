# 指标与监控（P3-004）

> 后端暴露 Prometheus 指标（JVM/HTTP/连接池自动采集 + 业务自定义计数），供本机 Prometheus/Grafana 或脚本抓取。

## 端点

```bash
curl -s http://localhost:8081/actuator/prometheus | head          # Prometheus 文本格式（~200+ 指标）
curl -s http://localhost:8081/actuator/metrics                    # 指标名清单（JSON）
curl -s http://localhost:8081/actuator/health                     # 健康（含子组件）
```

- 启用：`core/pom.xml` 增加 `micrometer-registry-prometheus`；`management.metrics.export.prometheus.enabled=true`。
- 共同标签：`application="lhjypt-core"`。

## 指标清单（节选）

| 类别 | 指标 |
|------|------|
| JVM | `jvm_memory_used_bytes`、`jvm_gc_pause_seconds`、`jvm_classes_loaded_classes` |
| HTTP | `http_server_requests_seconds_count/sum`（按 uri/status/method） |
| 连接池 | `hikaricp_connections_active`（2 个数据源）、`hikaricp_connections_pending` |
| 容量 | `lhjypt_capacity_load_pct`（综合水位 gauge） |
| 回测 | `lhjypt_backtest_runs_total{status}`、`lhjypt_backtest_duration_seconds`（Timer） |
| 行情刷新 | `lhjypt_market_refresh_total{result=ok\|error}`、`lhjypt_market_refresh_inserted_total` |
| 风控 | `lhjypt_circuit_trips_total{kind=strategy\|portfolio}` |

## 常用查询（PromQL）

```promql
# 请求速率（按接口）
sum by (uri) (rate(http_server_requests_seconds_count[5m]))
# P95 延迟
histogram_quantile(0.95, sum by (le, uri) (rate(http_server_requests_seconds_bucket[5m])))
# 回测失败率
rate(lhjypt_backtest_runs_total{status="FAILED"}[10m]) / rate(lhjypt_backtest_runs_total[10m])
# 行情刷新失败
increase(lhjypt_market_refresh_total{result="error"}[1h])
# 连接池压力
hikaricp_connections_active / hikaricp_connections_max
```

## 接入本机 Prometheus（可选）

已提供一键观测栈（Prometheus + Grafana，含预置数据源与看板）：`./scripts/obs.sh up`，详见 [observability.md](observability.md)。

手动接入示例：

```yaml
# prometheus.yml
scrape_configs:
  - job_name: lhjypt
    metrics_path: /actuator/prometheus
    static_configs:
      - targets: ['host.docker.internal:8081']   # 容器内抓宿主机（Lima 用 host.lima.internal）
```

Grafana 导入任意 JVM/Spring Boot 仪表盘即可（`application="lhjypt-core"` 过滤）。

## 扩展业务埋点

在服务中注入 `MetricsService`（`com.lhjypt.core.service.monitor`）并调用：
`orderSubmitted(mode)` / `backtestRun(status,ms)` / `marketRefresh(inserted)` / `marketRefreshFailure()` / `circuitTrip(kind)` / `capacityLoad(pct)`。
新增服务建议采用「主构造（含 MetricsService）+ 便捷构造（无指标，供单测）」模式，避免破坏既有测试；**勿在双构造 Bean 上省略 `@Autowired`**（详见 `docs/deploy.md` 教训）。