# P4-006 告警外发（Alertmanager） · 任务报告

- 任务：P4-006 观测栈告警外发（Prometheus → Alertmanager → webhook，钉钉/企业微信可注入）
- 依据：`docs/observability.md` / `delivery-report-p4.md §6`「告警外发」
- 状态：DONE

## 改动（纯新增，不改应用代码）

| 文件 | 说明 |
|------|------|
| `config/observability/docker-compose.yaml` | 新增 `lhjypt-alertmanager`(9093, v0.27.0) + `lhjypt-alert-sink`(python webhook 接收器) |
| `config/observability/alertmanager.yml.tmpl` | 路由/接收器模板，`__WEBHOOK__` 注入 |
| `config/observability/alert-sink.py` | 本地 webhook 接收器：打印收到的告警（验证/兜底） |
| `config/observability/prometheus.yml.tmpl` | 增 `alerting.alertmanagers=[lhjypt-alertmanager:9093]` |
| `scripts/obs.sh` | 生成 `alertmanager.yml`；`up` 改 `--force-recreate`；`status` 增 Alertmanager 活动告警 |
| `docs/observability.md` | 告警外发章节 + 演练记录 |

## 设计
- 路由 `group_by=[alertname]`，`group_wait=10s`，`group_interval=1m`，`repeat_interval=3h`；接收器 `default` 使用 `webhook_configs`，`send_resolved=true`。
- 默认 webhook = 内置 `lhjypt-alert-sink`（本地可验证）；**钉钉/企业微信**：`LHJYPT_ALERT_WEBHOOK=<转发网关>`（Alertmanager 无原生钉钉接收器，需网关做消息格式转换）。

## 验证（真实，端到端）

| 步骤 | 证据 |
|------|------|
| 启动 | `lhjypt-alertmanager`/`lhjypt-alert-sink` up；`/-/healthy`=OK |
| 注册 | Prometheus `/api/v1/alertmanagers` active=`http://lhjypt-alertmanager:9093/api/v2/alerts` |
| 触发 | 目标指向死地址 → 规则 `firing`；Alertmanager `/api/v2/alerts` active 1（critical） |
| 投递 | `docker logs lhjypt-alert-sink`：`ALERT status=firing name=LhjyptBackendDown severity=critical instance=127.0.0.1:9` + `RECEIVED groupStatus=firing alerts=1` |
| 恢复 | 目标恢复 → 规则 `inactive`；Alertmanager 活动 0；sink：`ALERT status=resolved` + `RECEIVED groupStatus=resolved alerts=1` |

## 边界
- 内置 sink 仅供本地验证；外发钉钉/企业微信需自备转发网关（或复用业务侧 `AlertNotifier`，其原生支持钉钉/企业微信格式）。
- 观测栈独立 compose，`down` 保留数据卷（`amdata` 亦保留）。
