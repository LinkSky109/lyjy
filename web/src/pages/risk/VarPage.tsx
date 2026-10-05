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
  KeyValue,
  Notice,
  Page,
  Select,
  Stat,
  Toolbar,
} from '../../components/ui';

interface VarResult {
  calcId?: string;
  scopeType?: string;
  scopeId?: string;
  method?: string;
  confidence?: number;
  horizonDays?: number;
  varValue?: number;
  sampleCount?: number;
  meanPnl?: number;
  stdPnl?: number;
  percentilePnl?: number;
  minPnl?: number;
  maxPnl?: number;
  riskBudgetLimit?: number;
  budgetBreached?: boolean;
  calcTime?: string;
}

interface VarCalculation {
  id?: number;
  calcId?: string;
  scopeType?: string;
  scopeId?: string;
  method?: string;
  confidence?: number;
  horizonDays?: number;
  varValue?: number;
  pnlHistJson?: string;
  calcTime?: string;
}

interface ComputeForm {
  scopeType: string;
  scopeId: string;
  method: string;
  confidence: string;
  horizonDays: string;
}

const SCOPE_TYPES = ['PORTFOLIO', 'ACCOUNT', 'STRATEGY'];
const METHODS = ['HISTORICAL', 'VAR_COV'];

const EMPTY_FORM: ComputeForm = {
  scopeType: 'PORTFOLIO',
  scopeId: 'PF001',
  method: 'HISTORICAL',
  confidence: '0.95',
  horizonDays: '1',
};

function pad(value: number): string {
  return String(value).padStart(2, '0');
}

function toLocalInput(date: Date): string {
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(
    date.getHours(),
  )}:${pad(date.getMinutes())}`;
}

function fmt(value: number | undefined | null): string {
  if (value === undefined || value === null) {
    return '-';
  }
  return Number(value).toLocaleString('zh-CN', { maximumFractionDigits: 4 });
}

export default function VarPage() {
  const now = new Date();
  const defaultFrom = toLocalInput(new Date(now.getTime() - 7 * 24 * 3600 * 1000));

  const [form, setForm] = useState<ComputeForm>(EMPTY_FORM);
  const [computing, setComputing] = useState(false);
  const [computeError, setComputeError] = useState<string | null>(null);
  const [result, setResult] = useState<VarResult | null>(null);

  const [scope, setScope] = useState<{ scopeType: string; scopeId: string } | null>(null);
  const [from, setFrom] = useState(defaultFrom);
  const [to, setTo] = useState(toLocalInput(now));
  const [range, setRange] = useState<{ from: string; to: string } | null>(null);

  const latest = useApi(
    () =>
      scope
        ? api.get<VarCalculation>('/api/var/latest', {
            scopeType: scope.scopeType,
            scopeId: scope.scopeId,
          })
        : Promise.resolve(null),
    [scope?.scopeType, scope?.scopeId],
  );

  const history = useApi(
    () =>
      scope && range
        ? api.get<VarCalculation[]>('/api/var/history', {
            scopeType: scope.scopeType,
            scopeId: scope.scopeId,
            from: range.from,
            to: range.to,
          })
        : Promise.resolve(null),
    [scope?.scopeType, scope?.scopeId, range?.from, range?.to],
  );

  const update = (key: keyof ComputeForm, value: string) =>
    setForm((prev) => ({ ...prev, [key]: value }));

  const compute = async () => {
    setComputing(true);
    setComputeError(null);
    try {
      const payload = {
        scopeType: form.scopeType,
        scopeId: form.scopeId.trim(),
        method: form.method,
        confidence: form.confidence ? Number(form.confidence) : undefined,
        horizonDays: form.horizonDays ? Number(form.horizonDays) : undefined,
      };
      const data = await api.post<VarResult>('/api/var/compute', payload);
      setResult(data);
      setScope({ scopeType: form.scopeType, scopeId: form.scopeId.trim() });
      setRange({ from, to });
    } catch (err) {
      setComputeError(errorText(err));
      setResult(null);
    } finally {
      setComputing(false);
    }
  };

  const query = () => {
    setScope({ scopeType: form.scopeType, scopeId: form.scopeId.trim() });
    setRange({ from, to });
  };

  const historyRows = history.data ?? [];

  return (
    <Page title="VaR 风险价值" subtitle="历史模拟与方差协方差法计算组合在险价值">
      <Card title="计算参数">
        <Toolbar>
          <Field label="作用域类型">
            <Select
              value={form.scopeType}
              onChange={(event) => update('scopeType', event.target.value)}
            >
              {SCOPE_TYPES.map((item) => (
                <option key={item} value={item}>
                  {item}
                </option>
              ))}
            </Select>
          </Field>
          <Field label="作用域 ID">
            <Input
              value={form.scopeId}
              onChange={(event) => update('scopeId', event.target.value)}
              placeholder="PF001"
            />
          </Field>
          <Field label="计算方法">
            <Select value={form.method} onChange={(event) => update('method', event.target.value)}>
              {METHODS.map((item) => (
                <option key={item} value={item}>
                  {item}
                </option>
              ))}
            </Select>
          </Field>
          <Field label="置信度" hint="如 0.95">
            <Input
              value={form.confidence}
              onChange={(event) => update('confidence', event.target.value)}
              placeholder="0.95"
            />
          </Field>
          <Field label="持有期（天）">
            <Input
              value={form.horizonDays}
              onChange={(event) => update('horizonDays', event.target.value)}
              placeholder="1"
            />
          </Field>
          <Button variant="primary" onClick={compute} disabled={computing || !form.scopeId.trim()}>
            {computing ? '计算中…' : '计算 VaR'}
          </Button>
          <Button onClick={query} disabled={!form.scopeId.trim()}>
            查询历史
          </Button>
        </Toolbar>
        {computeError && <Notice tone="danger">{computeError}</Notice>}
      </Card>

      {result && (
        <Card title={`计算结果${result.calcId ? `：${result.calcId}` : ''}`}>
          <Grid columns={4}>
            <Stat label="VaR 金额" value={fmt(result.varValue)} tone="danger" hint="正数表示可能损失" />
            <Stat label="样本数" value={result.sampleCount ?? '-'} tone="info" />
            <Stat label="分位损益" value={fmt(result.percentilePnl)} tone="warn" />
            <Stat
              label="风险预算"
              value={fmt(result.riskBudgetLimit)}
              tone={result.budgetBreached ? 'danger' : 'ok'}
              hint={result.budgetBreached ? '已突破预算' : '未突破'}
            />
          </Grid>
          <div style={{ margin: '12px 0' }}>
            <Badge tone={result.budgetBreached ? 'danger' : 'ok'}>
              {result.budgetBreached ? '突破风险预算' : '预算内'}
            </Badge>
            <Badge tone="muted">{`方法 ${result.method ?? '-'}`}</Badge>
            <Badge tone="muted">{`置信度 ${result.confidence ?? '-'}`}</Badge>
            <Badge tone="muted">{`持有期 ${result.horizonDays ?? '-'} 天`}</Badge>
          </div>
          <KeyValue
            items={[
              { label: '均值损益', value: fmt(result.meanPnl) },
              { label: '标准差', value: fmt(result.stdPnl) },
              { label: '区间最小损益', value: fmt(result.minPnl) },
              { label: '区间最大损益', value: fmt(result.maxPnl) },
              { label: '计算时间', value: result.calcTime ?? '-' },
            ]}
          />
        </Card>
      )}

      <Card title="最近一次记录">
        <AsyncState
          loading={latest.loading}
          error={latest.error}
          onRetry={latest.reload}
          empty={!scope && '请先计算或查询指定作用域'}
        >
          {latest.data && (
            <KeyValue
              items={[
                { label: '计算 ID', value: latest.data.calcId ?? '-' },
                { label: '作用域', value: `${latest.data.scopeType ?? '-'}/${latest.data.scopeId ?? '-'}` },
                { label: '方法', value: latest.data.method ?? '-' },
                { label: '置信度', value: latest.data.confidence ?? '-' },
                { label: '持有期（天）', value: latest.data.horizonDays ?? '-' },
                { label: 'VaR 金额', value: fmt(latest.data.varValue) },
                { label: '计算时间', value: latest.data.calcTime ?? '-' },
              ]}
            />
          )}
        </AsyncState>
      </Card>

      <Card title="历史记录">
        <Toolbar>
          <Field label="开始时间">
            <Input type="datetime-local" value={from} onChange={(event) => setFrom(event.target.value)} />
          </Field>
          <Field label="结束时间">
            <Input type="datetime-local" value={to} onChange={(event) => setTo(event.target.value)} />
          </Field>
          <Button onClick={query} disabled={!form.scopeId.trim()}>
            按范围查询
          </Button>
        </Toolbar>
        <AsyncState
          loading={history.loading}
          error={history.error}
          onRetry={history.reload}
          empty={!scope || !range ? '请先计算或查询' : historyRows.length === 0 && '该区间暂无记录'}
        >
          <DataTable
            columns={[
              { key: 'calcId', title: '计算 ID' },
              { key: 'method', title: '方法' },
              { key: 'confidence', title: '置信度', align: 'right' },
              { key: 'horizonDays', title: '持有期', align: 'right' },
              { key: 'varValue', title: 'VaR 金额', align: 'right', render: (row) => fmt(row.varValue) },
              { key: 'calcTime', title: '计算时间' },
            ]}
            rows={historyRows}
            rowKey={(row) => String(row.calcId ?? row.id)}
          />
        </AsyncState>
      </Card>
    </Page>
  );
}
