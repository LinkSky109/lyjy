import { useState } from 'react';
import { api, errorText } from '../../api/http';
import { useApi } from '../../hooks/useApi';
import {
  AsyncState,
  Badge,
  Button,
  Card,
  DataTable,
  Field,
  Grid,
  Input,
  Notice,
  Page,
  Select,
  Stat,
  statusTone,
  Tabs,
  Textarea,
  Toolbar,
} from '../../components/ui';

interface MetricSample {
  ts?: string;
  metric?: string;
  value?: number;
  labels?: string;
}

interface AlertRule {
  id?: number;
  ruleId: string;
  name?: string;
  metricKey?: string;
  condition?: string;
  threshold?: number;
  windowSec?: number;
  severity?: string;
  channelsJson?: string;
  enabled?: boolean;
  createTime?: string;
  updateTime?: string;
}

interface AlertEvent {
  id?: number;
  eventId: string;
  ruleId?: string;
  metricKey?: string;
  value?: string;
  severity?: string;
  message?: string;
  status?: string;
  ackBy?: string;
  ackTime?: string;
  createTime?: string;
}

interface RuleForm {
  ruleId: string;
  name: string;
  metricKey: string;
  condition: string;
  threshold: string;
  windowSec: string;
  severity: string;
  channelsJson: string;
  enabled: boolean;
}

const TABS = [
  { key: 'metrics', label: '指标' },
  { key: 'rules', label: '告警规则' },
  { key: 'alerts', label: '告警事件' },
];

const CONDITIONS = ['GT', 'LT', 'EQ', 'NEQ'];
const SEVERITIES = ['INFO', 'WARN', 'CRITICAL'];
const ALERT_STATUS = ['', 'OPEN', 'ACKED', 'CLOSED'];

const EMPTY_RULE: RuleForm = {
  ruleId: '',
  name: '',
  metricKey: 'system.cpu.load',
  condition: 'GT',
  threshold: '0.9',
  windowSec: '60',
  severity: 'WARN',
  channelsJson: '["log"]',
  enabled: true,
};

function pad(value: number): string {
  return String(value).padStart(2, '0');
}

function toLocalInput(date: Date): string {
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(
    date.getHours(),
  )}:${pad(date.getMinutes())}`;
}

export default function MonitorPage() {
  const now = new Date();

  const [tab, setTab] = useState('metrics');

  const latest = useApi(() => api.get<Record<string, unknown>>('/api/monitor/metrics/latest'), []);

  const [metricInput, setMetricInput] = useState('system.cpu.load');
  const [fromInput, setFromInput] = useState(toLocalInput(new Date(now.getTime() - 3 * 3600 * 1000)));
  const [toInput, setToInput] = useState(toLocalInput(now));
  const [range, setRange] = useState<{ metric: string; from: string; to: string } | null>(null);

  const history = useApi(
    () =>
      range
        ? api.get<MetricSample[]>('/api/monitor/metrics/history', {
            metric: range.metric,
            from: range.from,
            to: range.to,
          })
        : Promise.resolve(null),
    [range?.metric, range?.from, range?.to],
  );

  const rules = useApi(() => api.get<AlertRule[]>('/api/monitor/alert-rule/list'), []);
  const [ruleForm, setRuleForm] = useState<RuleForm>(EMPTY_RULE);
  const [savingRule, setSavingRule] = useState(false);
  const [ruleError, setRuleError] = useState<string | null>(null);
  const [ruleMessage, setRuleMessage] = useState<string | null>(null);
  const [togglingId, setTogglingId] = useState<string | null>(null);

  const [statusFilter, setStatusFilter] = useState('OPEN');
  const [severityFilter, setSeverityFilter] = useState('');
  const [ackBy, setAckBy] = useState('admin');
  const health = useApi(
    () =>
      api.get<{
        status: string;
        components?: Record<string, { status: string; components?: Record<string, { status: string }> }>;
      }>(
        '/actuator/health',
      ),
    [],
  );
  const alerts = useApi(
    () =>
      api.get<AlertEvent[]>('/api/monitor/alerts', {
        status: statusFilter || undefined,
      }),
    [statusFilter],
  );
  const [alertError, setAlertError] = useState<string | null>(null);
  const [actingId, setActingId] = useState<string | null>(null);

  const updateRule = (key: keyof RuleForm, value: string | boolean) =>
    setRuleForm((prev) => ({ ...prev, [key]: value }));

  const resetRuleForm = () => {
    setRuleForm(EMPTY_RULE);
    setRuleError(null);
    setRuleMessage(null);
  };

  const editRule = (rule: AlertRule) => {
    setRuleForm({
      ruleId: rule.ruleId ?? '',
      name: rule.name ?? '',
      metricKey: rule.metricKey ?? '',
      condition: rule.condition ?? 'GT',
      threshold: rule.threshold != null ? String(rule.threshold) : '',
      windowSec: rule.windowSec != null ? String(rule.windowSec) : '60',
      severity: rule.severity ?? 'WARN',
      channelsJson: rule.channelsJson ?? '["log"]',
      enabled: rule.enabled ?? true,
    });
    setRuleError(null);
    setRuleMessage(null);
    setTab('rules');
  };

  const saveRule = async () => {
    if (!ruleForm.ruleId.trim()) {
      setRuleError('ruleId 不能为空');
      return;
    }
    setSavingRule(true);
    setRuleError(null);
    setRuleMessage(null);
    try {
      const payload = {
        ruleId: ruleForm.ruleId.trim(),
        name: ruleForm.name.trim() || undefined,
        metricKey: ruleForm.metricKey.trim() || undefined,
        condition: ruleForm.condition,
        threshold: ruleForm.threshold ? Number(ruleForm.threshold) : undefined,
        windowSec: ruleForm.windowSec ? Number(ruleForm.windowSec) : undefined,
        severity: ruleForm.severity,
        channelsJson: ruleForm.channelsJson.trim() || undefined,
        enabled: ruleForm.enabled,
      };
      const message = await api.post<string>('/api/monitor/alert-rule', payload);
      setRuleMessage(message);
      rules.reload();
    } catch (err) {
      setRuleError(errorText(err));
    } finally {
      setSavingRule(false);
    }
  };

  const toggleRule = async (rule: AlertRule) => {
    setTogglingId(rule.ruleId);
    setRuleError(null);
    try {
      await api.post<string>(
        `/api/monitor/alert-rule/${encodeURIComponent(rule.ruleId)}/toggle`,
        undefined,
        { params: { enabled: !rule.enabled } },
      );
      rules.reload();
    } catch (err) {
      setRuleError(errorText(err));
    } finally {
      setTogglingId(null);
    }
  };

  const ackAlert = async (event: AlertEvent) => {
    setActingId(event.eventId);
    setAlertError(null);
    try {
      await api.post<string>(
        `/api/monitor/alerts/${encodeURIComponent(event.eventId)}/ack`,
        undefined,
        { params: { ackBy: ackBy.trim() || 'admin' } },
      );
      alerts.reload();
    } catch (err) {
      setAlertError(errorText(err));
    } finally {
      setActingId(null);
    }
  };

  const closeAlert = async (event: AlertEvent) => {
    setActingId(event.eventId);
    setAlertError(null);
    try {
      await api.post<string>(`/api/monitor/alerts/${encodeURIComponent(event.eventId)}/close`);
      alerts.reload();
    } catch (err) {
      setAlertError(errorText(err));
    } finally {
      setActingId(null);
    }
  };

  const queryHistory = () => {
    setRange({ metric: metricInput.trim(), from: fromInput, to: toInput });
  };

  const metricEntries = Object.entries(latest.data ?? {});
  const ruleRows = rules.data ?? [];
  const historyRows = history.data ?? [];
  const alertRows = alerts.data ?? [];
  const openAlerts = alertRows.filter((row) => row.status === 'OPEN').length;
  const filteredAlertRows = severityFilter
    ? alertRows.filter((row) => row.severity === severityFilter)
    : alertRows;
  const activeFilter =
    statusFilter === 'OPEN' && !severityFilter
      ? 'open'
      : !statusFilter && severityFilter === 'CRITICAL'
        ? 'critical'
        : !statusFilter && severityFilter === 'WARN'
          ? 'warn'
          : !statusFilter && !severityFilter
            ? 'all'
            : 'custom';

  const chooseFilter = (key: string) => {
    if (key === 'all') {
      setStatusFilter('');
      setSeverityFilter('');
    } else if (key === 'open') {
      setStatusFilter('OPEN');
      setSeverityFilter('');
    } else if (key === 'critical') {
      setStatusFilter('');
      setSeverityFilter('CRITICAL');
    } else {
      setStatusFilter('');
      setSeverityFilter('WARN');
    }
  };

  const alertsEmpty = !alerts.loading && filteredAlertRows.length === 0 &&
    '当前筛选没有告警事件。切换筛选或到「告警规则」确认阈值是否已配置。';
  const components = health.data?.components ?? {};

  return (
    <Page title="监控告警" subtitle="运行指标、告警规则与告警事件闭环">
      <Grid columns={4}>
        <Stat label="指标项" value={metricEntries.length} tone="info" />
        <Stat label="告警规则" value={ruleRows.length} tone="muted" />
        <Stat label="当前告警" value={alertRows.length} tone="muted" />
        <Stat label="未闭环" value={openAlerts} tone={openAlerts > 0 ? 'warn' : 'ok'} />
      </Grid>

      <Tabs tabs={TABS} active={tab} onChange={setTab} />

      {tab === 'metrics' && (
        <>
          <Card title="最新指标快照">
            <AsyncState
              loading={latest.loading}
              error={latest.error}
              onRetry={latest.reload}
              empty={metricEntries.length === 0 && '暂无指标'}
            >
              <DataTable
                columns={[
                  { key: 'metric', title: '指标', render: (row) => row[0] },
                  { key: 'value', title: '值', align: 'right', render: (row) => String(row[1]) },
                ]}
                rows={metricEntries}
                rowKey={(row) => String(row[0])}
              />
            </AsyncState>
          </Card>

          <Card title="指标历史">
            <Toolbar>
              <Field label="指标键">
                <Input
                  value={metricInput}
                  onChange={(event) => setMetricInput(event.target.value)}
                  placeholder="system.cpu.load"
                  list="monitor-metric-keys"
                />
              </Field>
              <datalist id="monitor-metric-keys">
                <option value="jvm.memory.used" />
                <option value="jvm.memory.max" />
                <option value="system.cpu.load" />
                <option value="system.cpu.count" />
                <option value="jvm.threads.count" />
              </datalist>
              <Field label="开始时间">
                <Input
                  type="datetime-local"
                  value={fromInput}
                  onChange={(event) => setFromInput(event.target.value)}
                />
              </Field>
              <Field label="结束时间">
                <Input
                  type="datetime-local"
                  value={toInput}
                  onChange={(event) => setToInput(event.target.value)}
                />
              </Field>
              <Button variant="primary" onClick={queryHistory} disabled={!metricInput.trim()}>
                查询
              </Button>
            </Toolbar>
            <AsyncState
              loading={history.loading}
              error={history.error}
              onRetry={history.reload}
              empty={!range ? '请选择指标与时间范围' : historyRows.length === 0 && '该区间暂无采样'}
            >
              <DataTable
                columns={[
                  { key: 'ts', title: '采样时间' },
                  { key: 'metric', title: '指标' },
                  { key: 'value', title: '值', align: 'right' },
                  { key: 'labels', title: '标签' },
                ]}
                rows={historyRows}
                rowKey={(row, index) => `${row.ts}-${row.metric}-${index}`}
              />
            </AsyncState>
          </Card>
        </>
      )}

      {tab === 'rules' && (
        <>
          <Card title={ruleForm.ruleId ? `编辑规则：${ruleForm.ruleId}` : '新建告警规则'}>
            <Toolbar>
              <Field label="规则 ID" hint="唯一标识，必需">
                <Input
                  value={ruleForm.ruleId}
                  onChange={(event) => updateRule('ruleId', event.target.value)}
                  placeholder="rule-cpu-high"
                />
              </Field>
              <Field label="规则名称">
                <Input
                  value={ruleForm.name}
                  onChange={(event) => updateRule('name', event.target.value)}
                  placeholder="CPU 负载过高"
                />
              </Field>
              <Field label="指标键">
                <Input
                  value={ruleForm.metricKey}
                  onChange={(event) => updateRule('metricKey', event.target.value)}
                  placeholder="system.cpu.load"
                />
              </Field>
              <Field label="条件">
                <Select
                  value={ruleForm.condition}
                  onChange={(event) => updateRule('condition', event.target.value)}
                >
                  {CONDITIONS.map((item) => (
                    <option key={item} value={item}>
                      {item}
                    </option>
                  ))}
                </Select>
              </Field>
              <Field label="阈值">
                <Input
                  value={ruleForm.threshold}
                  onChange={(event) => updateRule('threshold', event.target.value)}
                  placeholder="0.9"
                />
              </Field>
              <Field label="评估窗口（秒）">
                <Input
                  value={ruleForm.windowSec}
                  onChange={(event) => updateRule('windowSec', event.target.value)}
                  placeholder="60"
                />
              </Field>
              <Field label="严重级别">
                <Select
                  value={ruleForm.severity}
                  onChange={(event) => updateRule('severity', event.target.value)}
                >
                  {SEVERITIES.map((item) => (
                    <option key={item} value={item}>
                      {item}
                    </option>
                  ))}
                </Select>
              </Field>
              <Field label="启用">
                <Select
                  value={ruleForm.enabled ? 'true' : 'false'}
                  onChange={(event) => updateRule('enabled', event.target.value === 'true')}
                >
                  <option value="true">启用</option>
                  <option value="false">禁用</option>
                </Select>
              </Field>
            </Toolbar>
            <Field label="通知渠道（JSON 数组）" hint='如 ["log","email"]'>
              <Textarea
                value={ruleForm.channelsJson}
                onChange={(event) => updateRule('channelsJson', event.target.value)}
                rows={2}
              />
            </Field>
            <Toolbar>
              <Button variant="primary" onClick={saveRule} disabled={savingRule}>
                {savingRule ? '保存中…' : '保存规则'}
              </Button>
              <Button onClick={resetRuleForm} disabled={savingRule}>
                清空表单
              </Button>
            </Toolbar>
            {ruleError && <Notice tone="danger">{ruleError}</Notice>}
            {ruleMessage && <Notice tone="ok">{ruleMessage}</Notice>}
          </Card>

          <Card title="告警规则列表">
            <AsyncState
              loading={rules.loading}
              error={rules.error}
              onRetry={rules.reload}
              empty={ruleRows.length === 0 && '暂无告警规则'}
            >
              <DataTable
                columns={[
                  { key: 'ruleId', title: '规则 ID' },
                  { key: 'name', title: '名称' },
                  { key: 'metricKey', title: '指标' },
                  { key: 'condition', title: '条件' },
                  { key: 'threshold', title: '阈值', align: 'right' },
                  { key: 'windowSec', title: '窗口(s)', align: 'right' },
                  {
                    key: 'severity',
                    title: '级别',
                    render: (row) => <Badge tone={statusTone(row.severity)}>{row.severity}</Badge>,
                  },
                  {
                    key: 'enabled',
                    title: '启用',
                    render: (row) => (
                      <label className="switch">
                        <input
                          type="checkbox"
                          aria-label={`${row.ruleId} 启用开关`}
                          checked={row.enabled ?? false}
                          disabled={togglingId === row.ruleId}
                          onChange={() => toggleRule(row)}
                        />
                        <span className="switch-track" />
                      </label>
                    ),
                  },
                  {
                    key: 'actions',
                    title: '操作',
                    align: 'right',
                    render: (row) => (
                      <Button size="sm" onClick={() => editRule(row)}>
                        编辑
                      </Button>
                    ),
                  },
                ]}
                rows={ruleRows}
                rowKey={(row) => String(row.ruleId)}
              />
            </AsyncState>
          </Card>
        </>
      )}

      {tab === 'alerts' && (
        <Card title="告警事件">
          <Toolbar>
            <div className="filter-bar" role="group" aria-label="告警筛选">
              {[
                { key: 'all', label: '全部' },
                { key: 'open', label: '未确认' },
                { key: 'critical', label: '严重' },
                { key: 'warn', label: '警告' },
              ].map((item) => (
                <button
                  key={item.key}
                  type="button"
                  className="chip"
                  aria-pressed={activeFilter === item.key}
                  onClick={() => chooseFilter(item.key)}
                >
                  {item.label}
                </button>
              ))}
            </div>
            <Field label="确认人">
              <Input value={ackBy} onChange={(event) => setAckBy(event.target.value)} placeholder="admin" />
            </Field>
            <Button onClick={() => alerts.reload()}>刷新</Button>
          </Toolbar>

          <div className="status-strip mt-1 mb-3">
            <div className="status-item">
              <span className="status-label">核心服务</span>
              <span className="status-value">
                <span
                  className={`dot ${health.data?.status === 'UP' ? 'dot-ok' : health.data ? 'dot-danger' : ''}`}
                />
                {health.data?.status ?? '未上报'}
              </span>
            </div>
            <div className="status-item">
              <span className="status-label">PostgreSQL</span>
              <span className="status-value">
                <span
                  className={`dot ${
                    components.db?.components?.primaryDataSource?.status === 'UP'
                      ? 'dot-ok'
                      : components.db
                        ? 'dot-danger'
                        : ''
                  }`}
                />
                {components.db?.components?.primaryDataSource?.status ?? '未上报'}
              </span>
            </div>
            <div className="status-item">
              <span className="status-label">ClickHouse</span>
              <span className="status-value">
                <span
                  className={`dot ${
                    components.db?.components?.clickHouseDataSource?.status === 'UP'
                      ? 'dot-ok'
                      : components.db
                        ? 'dot-danger'
                        : ''
                  }`}
                />
                {components.db?.components?.clickHouseDataSource?.status ?? '未上报'}
              </span>
            </div>
            <div className="status-item">
              <span className="status-label">Redis</span>
              <span className="status-value">
                <span
                  className={`dot ${components.redis?.status === 'UP' ? 'dot-ok' : components.redis ? 'dot-danger' : ''}`}
                />
                {components.redis?.status ?? '未上报'}
              </span>
            </div>
          </div>

          {alertError && <Notice tone="danger">{alertError}</Notice>}
          <AsyncState
            loading={alerts.loading}
            error={alerts.error}
            onRetry={alerts.reload}
            empty={alertsEmpty}
          >
            <DataTable
              columns={[
                { key: 'eventId', title: '事件号' },
                { key: 'metricKey', title: '指标' },
                { key: 'value', title: '触发值' },
                {
                  key: 'severity',
                  title: '级别',
                  render: (row) => <Badge tone={statusTone(row.severity)}>{row.severity}</Badge>,
                },
                {
                  key: 'status',
                  title: '状态',
                  render: (row) => <Badge tone={statusTone(row.status)}>{row.status}</Badge>,
                },
                { key: 'ackBy', title: '确认人' },
                { key: 'message', title: '说明' },
                { key: 'createTime', title: '时间' },
                {
                  key: 'actions',
                  title: '操作',
                  align: 'right',
                  render: (row) =>
                    row.status === 'OPEN' ? (
                      <Button
                        size="sm"
                        disabled={actingId === row.eventId}
                        onClick={() => ackAlert(row)}
                      >
                        确认
                      </Button>
                    ) : row.status !== 'CLOSED' ? (
                      <Button
                        size="sm"
                        variant="danger"
                        disabled={actingId === row.eventId}
                        onClick={() => closeAlert(row)}
                      >
                        关闭
                      </Button>
                    ) : (
                      <span className="muted">-</span>
                    ),
                },
              ]}
              rows={filteredAlertRows}
              rowKey={(row) => String(row.eventId)}
            />
          </AsyncState>
        </Card>
      )}
    </Page>
  );
}
