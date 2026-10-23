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
  Modal,
  Notice,
  Page,
  Select,
  Stat,
  statusTone,
  Textarea,
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

interface RiskBudget {
  id?: number;
  budgetId: string;
  scopeType?: string;
  scopeId?: string;
  budgetType?: string;
  limitValue?: number;
  currentValue?: number;
  usageRate?: number;
  status?: string;
  enabled?: boolean;
}

interface RiskRuleView {
  ruleCode: string;
  ruleName: string;
  enabled: boolean;
  configured: boolean;
  thresholdParams?: string;
  defaultThresholdParams?: string;
  editable?: boolean;
  action?: string;
  updatedAt?: string;
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
  const budgets = useApi(
    () => api.get<RiskBudget[]>('/api/risk/budget/list', { includeDisabled: true }),
    [],
  );
  const rulesApi = useApi(() => api.get<RiskRuleView[]>('/api/risk/rules'), []);

  const [editingBudget, setEditingBudget] = useState<RiskBudget | null>(null);
  const [budgetLimit, setBudgetLimit] = useState('');
  const [budgetEnabled, setBudgetEnabled] = useState(true);
  const [budgetError, setBudgetError] = useState<string | null>(null);
  const [budgetNotice, setBudgetNotice] = useState<string | null>(null);
  const [budgetBusy, setBudgetBusy] = useState(false);
  const [togglingRule, setTogglingRule] = useState<string | null>(null);
  const [editingRule, setEditingRule] = useState<RiskRuleView | null>(null);
  const [ruleParams, setRuleParams] = useState('');
  const [ruleAction, setRuleAction] = useState('REJECT');
  const [ruleError, setRuleError] = useState<string | null>(null);
  const [savingRule, setSavingRule] = useState(false);

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

  const saveBudget = async (budget: RiskBudget, limit: number, enabled: boolean) => {
    setBudgetBusy(true);
    setBudgetError(null);
    setBudgetNotice(null);
    try {
      await api.post<RiskBudget>('/api/risk/budget/save', {
        ...budget,
        limitValue: limit,
        enabled,
      });
      setBudgetNotice(`风险预算 ${budget.budgetId} 已更新`);
      setEditingBudget(null);
      budgets.reload();
      return true;
    } catch (err) {
      setBudgetError(errorText(err));
      return false;
    } finally {
      setBudgetBusy(false);
    }
  };

  const toggleBudget = async (budget: RiskBudget, enabled: boolean) => {
    if (budget.limitValue == null) {
      setBudgetError('该预算缺少上限值，无法通过开关保存。请先编辑阈值。');
      return;
    }
    await saveBudget(budget, budget.limitValue, enabled);
  };

  const toggleRule = async (rule: RiskRuleView, enabled: boolean) => {
    setTogglingRule(rule.ruleCode);
    setBudgetError(null);
    setBudgetNotice(null);
    try {
      await api.post<RiskRuleView>(
        `/api/risk/rules/${encodeURIComponent(rule.ruleCode)}/toggle`,
        undefined,
        { params: { enabled } },
      );
      setBudgetNotice(`规则 ${rule.ruleCode} 已${enabled ? '启用' : '停用'}`);
      rulesApi.reload();
    } catch (err) {
      setBudgetError(errorText(err));
    } finally {
      setTogglingRule(null);
    }
  };

  const openRule = (rule: RiskRuleView) => {
    setEditingRule(rule);
    setRuleParams(rule.thresholdParams ?? rule.defaultThresholdParams ?? '{}');
    setRuleAction(rule.action ?? 'REJECT');
    setRuleError(null);
  };

  const saveRule = async () => {
    if (!editingRule) {
      return;
    }
    setSavingRule(true);
    setRuleError(null);
    try {
      await api.put<RiskRuleView>(
        `/api/risk/rules/${encodeURIComponent(editingRule.ruleCode)}`,
        {
          thresholdParams: ruleParams.trim(),
          action: ruleAction,
          operator: 'ui',
        },
      );
      setBudgetNotice(`规则 ${editingRule.ruleCode} 阈值已更新`);
      setEditingRule(null);
      rulesApi.reload();
    } catch (err) {
      setRuleError(errorText(err));
    } finally {
      setSavingRule(false);
    }
  };

  const openBudget = (budget: RiskBudget) => {
    setEditingBudget(budget);
    setBudgetLimit(budget.limitValue != null ? String(budget.limitValue) : '');
    setBudgetEnabled(budget.enabled ?? true);
    setBudgetError(null);
  };

  const submitBudget = () => {
    if (!editingBudget) {
      return;
    }
    const limit = Number(budgetLimit);
    if (!Number.isFinite(limit) || limit <= 0) {
      setBudgetError('预算上限必须为大于 0 的数值。');
      return;
    }
    saveBudget(editingBudget, limit, budgetEnabled);
  };

  const blacklistRows = (blacklist.data ?? []).map((symbol) => ({ symbol }));
  const violations = result?.violations ?? [];
  const budgetRows = budgets.data ?? [];
  const ruleRows = rulesApi.data ?? [];

  return (
    <Page title="事前风控" subtitle="下单前风控试算、黑名单管理与拦截日志">
      <Grid columns={4}>
        <Stat
          label="黑名单标的"
          value={blacklistRows.length}
          tone={blacklistRows.length > 0 ? 'warn' : 'ok'}
        />
        <Stat label="拦截记录" value={intercepts.data?.length ?? 0} tone="info" />
        <Stat
          label="风险预算规则"
          value={budgetRows.length}
          tone={budgetRows.some((row) => row.status === 'BLOCK') ? 'danger' : 'muted'}
        />
        <Stat
          label="最近试算"
          value={result ? (result.passed ? '通过' : '拦截') : '-'}
          tone={result ? (result.passed ? 'ok' : 'danger') : 'muted'}
        />
      </Grid>

      <Card
        title="风险预算规则"
        actions={
          <>
            <span className="muted">开关即时生效 · 阈值弹窗修改</span>
            <Button size="sm" onClick={() => budgets.reload()} disabled={budgetBusy}>
              刷新
            </Button>
          </>
        }
      >
        {budgetError && <Notice tone="danger" live>{budgetError}</Notice>}
        {budgetNotice && <Notice tone="ok" live>{budgetNotice}</Notice>}
        <AsyncState
          loading={budgets.loading}
          error={budgets.error}
          onRetry={budgets.reload}
          empty={
            !budgets.loading &&
            budgetRows.length === 0 &&
            '暂无风险预算规则。可先通过 VaR 页刷新预算，或调用后端预算保存接口创建。'
          }
        >
          <DataTable
            columns={[
              { key: 'budgetId', title: '预算 ID' },
              { key: 'scopeType', title: '范围类型' },
              { key: 'scopeId', title: '范围' },
              { key: 'budgetType', title: '类型' },
              {
                key: 'limitValue',
                title: '上限',
                align: 'right',
                render: (row: RiskBudget) => (row.limitValue != null ? row.limitValue.toLocaleString('zh-CN') : '-'),
              },
              {
                key: 'currentValue',
                title: '当前占用',
                align: 'right',
                render: (row: RiskBudget) => (row.currentValue != null ? row.currentValue.toLocaleString('zh-CN') : '-'),
              },
              {
                key: 'usageRate',
                title: '占用率',
                align: 'right',
                render: (row: RiskBudget) => {
                  if (row.usageRate == null) {
                    return '-';
                  }
                  const percent = Math.min(100, Math.max(0, row.usageRate * 100));
                  const tone = percent >= 100 ? 'danger' : percent >= 80 ? 'warn' : '';
                  return (
                    <div className="usage-cell">
                      <span className="num">{(row.usageRate * 100).toFixed(1)}%</span>
                      <span className={`usage-track ${tone}`}>
                        <span className="usage-fill" style={{ width: `${percent}%` }} />
                      </span>
                    </div>
                  );
                },
              },
              {
                key: 'status',
                title: '状态',
                render: (row: RiskBudget) => (
                  <Badge tone={statusTone(row.status)}>{row.status ?? '-'}</Badge>
                ),
              },
              {
                key: 'enabled',
                title: '启用',
                render: (row: RiskBudget) => (
                  <label className="switch">
                    <input
                      type="checkbox"
                      aria-label={`${row.budgetId} 启用开关`}
                      checked={row.enabled ?? false}
                      disabled={budgetBusy}
                      onChange={(event) => toggleBudget(row, event.target.checked)}
                    />
                    <span className="switch-track" />
                  </label>
                ),
              },
              {
                key: 'actions',
                title: '阈值',
                align: 'right',
                render: (row: RiskBudget) => (
                  <Button size="sm" onClick={() => openBudget(row)} disabled={budgetBusy}>
                    编辑
                  </Button>
                ),
              },
            ]}
            rows={budgetRows}
            rowKey={(row: RiskBudget) => row.budgetId}
            empty="暂无风险预算规则"
          />
        </AsyncState>
      </Card>

      <Card
        title="事前风控规则"
        actions={
          <>
            <span className="muted">开关即时生效 · 阈值当前只读</span>
            <Button size="sm" onClick={() => rulesApi.reload()} disabled={togglingRule !== null}>
              刷新
            </Button>
          </>
        }
      >
        <AsyncState
          loading={rulesApi.loading}
          error={rulesApi.error}
          onRetry={rulesApi.reload}
          empty={!rulesApi.loading && ruleRows.length === 0 && '暂无事前风控规则。'}
        >
          <DataTable
            columns={[
              { key: 'ruleCode', title: '规则编码' },
              { key: 'ruleName', title: '规则名称' },
              {
                key: 'action',
                title: '动作',
                render: (row: RiskRuleView) => <Badge tone={row.action === 'WARN' ? 'warn' : 'danger'}>{row.action ?? 'REJECT'}</Badge>,
              },
              {
                key: 'thresholdParams',
                title: '阈值参数',
                render: (row: RiskRuleView) => {
                  const params = row.thresholdParams ?? row.defaultThresholdParams;
                  return params ? (
                    <code className="inline-code">{params}</code>
                  ) : (
                    <span className="muted">规则内置</span>
                  );
                },
              },
              {
                key: 'configured',
                title: '来源',
                render: (row: RiskRuleView) => (
                  <Badge tone={row.configured ? 'info' : 'muted'}>{row.configured ? '配置覆盖' : '默认'}</Badge>
                ),
              },
              {
                key: 'enabled',
                title: '启用',
                render: (row: RiskRuleView) => (
                  <label className="switch">
                    <input
                      type="checkbox"
                      aria-label={`${row.ruleCode} 启用开关`}
                      checked={row.enabled}
                      disabled={togglingRule === row.ruleCode}
                      onChange={(event) => toggleRule(row, event.target.checked)}
                    />
                    <span className="switch-track" />
                  </label>
                ),
              },
              {
                key: 'actions',
                title: '阈值',
                align: 'right',
                render: (row: RiskRuleView) =>
                  row.editable ? (
                    <Button size="sm" onClick={() => openRule(row)}>
                      编辑
                    </Button>
                  ) : (
                    <span className="muted">不支持</span>
                  ),
              },
            ]}
            rows={ruleRows}
            rowKey={(row: RiskRuleView) => row.ruleCode}
            empty="暂无事前风控规则"
          />
        </AsyncState>
      </Card>

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

        {checkError && <Notice tone="danger" live>{checkError}</Notice>}

        {result && (
          <div className="mt-3">
            <div className="mb-2">
              <Badge tone={result.passed ? 'ok' : 'danger'}>{result.passed ? '通过' : '拦截'}</Badge>
              <span className="muted ml-2">
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
          <Button onClick={addBlacklist} disabled={acting || !blacklistInput.trim()}>
            添加
          </Button>
        </Toolbar>

        {actionError && <Notice tone="danger" live>{actionError}</Notice>}

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

      <Modal
        open={!!editingBudget}
        title={`编辑风险预算${editingBudget ? ` · ${editingBudget.budgetId}` : ''}`}
        onClose={() => setEditingBudget(null)}
        footer={
          <>
            <Button onClick={() => setEditingBudget(null)} disabled={budgetBusy}>
              取消
            </Button>
            <Button variant="primary" onClick={submitBudget} disabled={budgetBusy}>
              {budgetBusy ? '保存中…' : '保存'}
            </Button>
          </>
        }
      >
        <Field label="预算上限 limitValue" error={budgetError ?? undefined}>
          <Input
            value={budgetLimit}
            onChange={(event) => {
              setBudgetLimit(event.target.value);
              setBudgetError(null);
            }}
            inputMode="decimal"
            aria-invalid={budgetError ? 'true' : undefined}
          />
        </Field>
        <Field label="启用状态">
          <label className="switch mt-1">
            <input
              type="checkbox"
              aria-label="启用风险预算"
              checked={budgetEnabled}
              onChange={(event) => setBudgetEnabled(event.target.checked)}
            />
            <span className="switch-track" />
          </label>
        </Field>
        <Notice tone="info">保存后立即影响事中风险预算校验，变更记录由后端审计。</Notice>
      </Modal>
    </Page>
  );
}
