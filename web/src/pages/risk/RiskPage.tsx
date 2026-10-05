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
  Toolbar,
} from '../../components/ui';

interface RuleViolation {
  ruleCode: string;
  ruleName: string;
  message: string;
  action: string;
}

interface RiskCheckResult {
  passed: boolean;
  violations: RuleViolation[];
}

interface RiskInterceptLog {
  id?: number;
  orderId?: string;
  accountId?: string;
  strategyId?: string;
  symbol?: string;
  ruleCode?: string;
  ruleName?: string;
  reason?: string;
  direction?: string;
  price?: number;
  quantity?: number;
  createTime?: string;
}

interface TradeForm {
  symbol: string;
  exchange: string;
  direction: string;
  offsetFlag: string;
  orderType: string;
  price: string;
  quantity: string;
  strategyId: string;
  accountId: string;
}

const DIRECTIONS = ['BUY', 'SELL'];
const OFFSET_FLAGS = ['OPEN', 'CLOSE', 'CLOSE_TODAY', 'CLOSE_YESTERDAY'];
const ORDER_TYPES = ['LIMIT', 'MARKET', 'FAK', 'FOK'];

const EMPTY_FORM: TradeForm = {
  symbol: 'IF2409',
  exchange: 'CFFEX',
  direction: 'BUY',
  offsetFlag: 'OPEN',
  orderType: 'LIMIT',
  price: '3800',
  quantity: '1',
  strategyId: '',
  accountId: '',
};

export default function RiskPage() {
  const intercepts = useApi(() => api.get<RiskInterceptLog[]>('/api/risk/intercepts'), []);
  const blacklist = useApi(() => api.get<string[]>('/api/risk/blacklist'), []);

  const [form, setForm] = useState<TradeForm>(EMPTY_FORM);
  const [checking, setChecking] = useState(false);
  const [checkError, setCheckError] = useState<string | null>(null);
  const [result, setResult] = useState<RiskCheckResult | null>(null);

  const [blacklistInput, setBlacklistInput] = useState('');
  const [actionError, setActionError] = useState<string | null>(null);
  const [acting, setActing] = useState(false);

  const update = (key: keyof TradeForm, value: string) =>
    setForm((prev) => ({ ...prev, [key]: value }));

  const submitCheck = async () => {
    setChecking(true);
    setCheckError(null);
    try {
      const payload = {
        symbol: form.symbol.trim(),
        exchange: form.exchange.trim() || undefined,
        direction: form.direction,
        offsetFlag: form.offsetFlag,
        orderType: form.orderType,
        price: form.price ? Number(form.price) : undefined,
        quantity: form.quantity ? Number(form.quantity) : undefined,
        strategyId: form.strategyId.trim() || undefined,
        accountId: form.accountId.trim() || undefined,
      };
      const data = await api.post<RiskCheckResult>('/api/risk/check', payload);
      setResult(data);
    } catch (err) {
      setCheckError(errorText(err));
      setResult(null);
    } finally {
      setChecking(false);
    }
  };

  const addBlacklist = async () => {
    const symbol = blacklistInput.trim();
    if (!symbol) {
      return;
    }
    setActing(true);
    setActionError(null);
    try {
      await api.post<void>(`/api/risk/blacklist/${encodeURIComponent(symbol)}`);
      setBlacklistInput('');
      blacklist.reload();
    } catch (err) {
      setActionError(errorText(err));
    } finally {
      setActing(false);
    }
  };

  const removeBlacklist = async (symbol: string) => {
    setActing(true);
    setActionError(null);
    try {
      await api.del<void>(`/api/risk/blacklist/${encodeURIComponent(symbol)}`);
      blacklist.reload();
    } catch (err) {
      setActionError(errorText(err));
    } finally {
      setActing(false);
    }
  };

  const blacklistRows = (blacklist.data ?? []).map((symbol) => ({ symbol }));
  const violations = result?.violations ?? [];

  return (
    <Page title="事前风控" subtitle="下单前风控试算、黑名单管理与拦截日志">
      <Grid columns={3}>
        <Stat
          label="黑名单标的"
          value={blacklistRows.length}
          tone={blacklistRows.length > 0 ? 'warn' : 'ok'}
        />
        <Stat label="拦截记录" value={intercepts.data?.length ?? 0} tone="info" />
        <Stat
          label="最近试算"
          value={result ? (result.passed ? '通过' : '拦截') : '-'}
          tone={result ? (result.passed ? 'ok' : 'danger') : 'muted'}
        />
      </Grid>

      <Card title="风控试算" actions={<span className="muted">POST /api/risk/check</span>}>
        <Toolbar>
          <Field label="标的代码">
            <Input
              value={form.symbol}
              onChange={(event) => update('symbol', event.target.value)}
              placeholder="IF2409"
            />
          </Field>
          <Field label="交易所">
            <Input
              value={form.exchange}
              onChange={(event) => update('exchange', event.target.value)}
              placeholder="CFFEX"
            />
          </Field>
          <Field label="方向">
            <Select value={form.direction} onChange={(event) => update('direction', event.target.value)}>
              {DIRECTIONS.map((item) => (
                <option key={item} value={item}>
                  {item}
                </option>
              ))}
            </Select>
          </Field>
          <Field label="开平">
            <Select value={form.offsetFlag} onChange={(event) => update('offsetFlag', event.target.value)}>
              {OFFSET_FLAGS.map((item) => (
                <option key={item} value={item}>
                  {item}
                </option>
              ))}
            </Select>
          </Field>
          <Field label="订单类型">
            <Select value={form.orderType} onChange={(event) => update('orderType', event.target.value)}>
              {ORDER_TYPES.map((item) => (
                <option key={item} value={item}>
                  {item}
                </option>
              ))}
            </Select>
          </Field>
          <Field label="委托价格">
            <Input
              value={form.price}
              onChange={(event) => update('price', event.target.value)}
              placeholder="3800"
            />
          </Field>
          <Field label="委托数量">
            <Input
              value={form.quantity}
              onChange={(event) => update('quantity', event.target.value)}
              placeholder="1"
            />
          </Field>
          <Field label="策略 ID">
            <Input
              value={form.strategyId}
              onChange={(event) => update('strategyId', event.target.value)}
              placeholder="可选"
            />
          </Field>
          <Field label="账户 ID">
            <Input
              value={form.accountId}
              onChange={(event) => update('accountId', event.target.value)}
              placeholder="可选"
            />
          </Field>
          <Button variant="primary" onClick={submitCheck} disabled={checking}>
            {checking ? '校验中…' : '执行试算'}
          </Button>
        </Toolbar>

        {checkError && <Notice tone="danger">{checkError}</Notice>}

        {result && (
          <div style={{ marginTop: 12 }}>
            <div style={{ marginBottom: 8 }}>
              <Badge tone={result.passed ? 'ok' : 'danger'}>{result.passed ? '通过' : '拦截'}</Badge>
              <span className="muted" style={{ marginLeft: 8 }}>
                违规 {violations.length} 项
              </span>
            </div>
            <DataTable
              columns={[
                { key: 'ruleCode', title: '规则编码' },
                { key: 'ruleName', title: '规则名称' },
                { key: 'message', title: '说明' },
                {
                  key: 'action',
                  title: '动作',
                  render: (row) => <Badge tone={statusTone(row.action)}>{row.action}</Badge>,
                },
              ]}
              rows={violations}
              empty="无违规项"
              rowKey={(row) => String(row.ruleCode)}
            />
          </div>
        )}
      </Card>

      <Card title="黑名单管理">
        <Toolbar>
          <Field label="标的代码">
            <Input
              value={blacklistInput}
              onChange={(event) => setBlacklistInput(event.target.value)}
              placeholder="添加到黑名单"
            />
          </Field>
          <Button variant="primary" onClick={addBlacklist} disabled={acting || !blacklistInput.trim()}>
            添加
          </Button>
        </Toolbar>

        {actionError && <Notice tone="danger">{actionError}</Notice>}

        <AsyncState
          loading={blacklist.loading}
          error={blacklist.error}
          onRetry={blacklist.reload}
          empty={blacklistRows.length === 0 && '黑名单为空'}
        >
          <DataTable
            columns={[
              { key: 'symbol', title: '标的代码' },
              {
                key: 'action',
                title: '操作',
                align: 'right',
                render: (row) => (
                  <Button
                    variant="danger"
                    size="sm"
                    disabled={acting}
                    onClick={() => removeBlacklist(row.symbol)}
                  >
                    移除
                  </Button>
                ),
              },
            ]}
            rows={blacklistRows}
            rowKey={(row) => String(row.symbol)}
          />
        </AsyncState>
      </Card>

      <Card title="拦截日志">
        <AsyncState
          loading={intercepts.loading}
          error={intercepts.error}
          onRetry={intercepts.reload}
          empty={!intercepts.loading && (intercepts.data ?? []).length === 0 && '暂无拦截记录'}
        >
          <DataTable
            columns={[
              { key: 'createTime', title: '时间' },
              { key: 'symbol', title: '标的' },
              { key: 'direction', title: '方向' },
              { key: 'ruleCode', title: '规则编码' },
              { key: 'ruleName', title: '规则名称' },
              { key: 'reason', title: '拦截原因' },
              { key: 'accountId', title: '账户' },
            ]}
            rows={intercepts.data ?? []}
            rowKey={(row) => String(row.id ?? `${row.orderId}-${row.ruleCode}`)}
          />
        </AsyncState>
      </Card>
    </Page>
  );
}
