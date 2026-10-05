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
  JsonBlock,
  Notice,
  Page,
  Select,
  Stat,
  statusTone,
  Tabs,
  Textarea,
  Toolbar,
} from '../../components/ui';

interface IntradayRiskRule {
  id?: number;
  ruleId: string;
  name?: string;
  ruleType?: string;
  enabled?: boolean;
  accountId?: string;
  strategyId?: string;
  symbol?: string;
  thresholdParams?: string;
  actionLevel?: string;
  cooldownSec?: number;
  createTime?: string;
  updateTime?: string;
}

interface RiskDispositionLog {
  id?: number;
  dispositionId?: string;
  triggerRule?: string;
  scopeType?: string;
  scopeId?: string;
  action?: string;
  params?: string;
  result?: string;
  confirmBy?: string;
  message?: string;
  createTime?: string;
  updateTime?: string;
}

type AccountStatus = Record<string, unknown>;

interface RuleForm {
  ruleId: string;
  name: string;
  ruleType: string;
  enabled: boolean;
  accountId: string;
  strategyId: string;
  symbol: string;
  thresholdParams: string;
  actionLevel: string;
  cooldownSec: string;
}

const RULE_TYPES = ['POSITION_LIMIT', 'LOSS_LIMIT', 'FUND_RISK', 'ORDER_RATE', 'VAR_LIMIT'];
const ACTION_LEVELS = ['WARN', 'BLOCK', 'FORCE_CLOSE'];
const SCOPE_TYPES = ['ACCOUNT', 'STRATEGY', 'SYMBOL'];

const EMPTY_RULE: RuleForm = {
  ruleId: '',
  name: '',
  ruleType: 'POSITION_LIMIT',
  enabled: true,
  accountId: '',
  strategyId: '',
  symbol: '',
  thresholdParams: '{}',
  actionLevel: 'WARN',
  cooldownSec: '60',
};

const TABS = [
  { key: 'rule', label: '规则' },
  { key: 'status', label: '账户状态' },
  { key: 'disposition', label: '处置与限制' },
];

export default function IntradayPage() {
  const rules = useApi(() => api.get<IntradayRiskRule[]>('/api/risk/intraday/rule/list'), []);
  const dispositions = useApi(
    () => api.get<RiskDispositionLog[]>('/api/risk/intraday/disposition/list'),
    [],
  );
  const blocks = useApi(() => api.get<string[]>('/api/risk/intraday/block/list'), []);

  const [tab, setTab] = useState('rule');

  const [ruleForm, setRuleForm] = useState<RuleForm>(EMPTY_RULE);
  const [saving, setSaving] = useState(false);
  const [ruleError, setRuleError] = useState<string | null>(null);
  const [ruleMessage, setRuleMessage] = useState<string | null>(null);
  const [togglingId, setTogglingId] = useState<string | null>(null);

  const [statusAccount, setStatusAccount] = useState('ACC001');
  const [committedAccount, setCommittedAccount] = useState('');
  const status = useApi(
    () =>
      committedAccount
        ? api.get<AccountStatus>(
            `/api/risk/intraday/status/${encodeURIComponent(committedAccount)}`,
          )
        : Promise.resolve(null),
    [committedAccount],
  );

  const [operator, setOperator] = useState('risk_officer');
  const [confirmingId, setConfirmingId] = useState<string | null>(null);
  const [dispositionError, setDispositionError] = useState<string | null>(null);
  const [dispositionMessage, setDispositionMessage] = useState<string | null>(null);
  const [evaluating, setEvaluating] = useState(false);

  const [releaseScopeType, setReleaseScopeType] = useState('ACCOUNT');
  const [releaseScopeId, setReleaseScopeId] = useState('');
  const [blockError, setBlockError] = useState<string | null>(null);
  const [releasing, setReleasing] = useState(false);

  const updateRule = (key: keyof RuleForm, value: string | boolean) =>
    setRuleForm((prev) => ({ ...prev, [key]: value }));

  const resetRuleForm = () => {
    setRuleForm(EMPTY_RULE);
    setRuleError(null);
    setRuleMessage(null);
  };

  const editRule = (rule: IntradayRiskRule) => {
    setRuleForm({
      ruleId: rule.ruleId ?? '',
      name: rule.name ?? '',
      ruleType: rule.ruleType ?? 'POSITION_LIMIT',
      enabled: rule.enabled ?? true,
      accountId: rule.accountId ?? '',
      strategyId: rule.strategyId ?? '',
      symbol: rule.symbol ?? '',
      thresholdParams: rule.thresholdParams ?? '{}',
      actionLevel: rule.actionLevel ?? 'WARN',
      cooldownSec: rule.cooldownSec != null ? String(rule.cooldownSec) : '60',
    });
    setRuleError(null);
    setRuleMessage(null);
    setTab('rule');
  };

  const saveRule = async () => {
    if (!ruleForm.ruleId.trim()) {
      setRuleError('ruleId 不能为空');
      return;
    }
    setSaving(true);
    setRuleError(null);
    setRuleMessage(null);
    try {
      const payload = {
        ruleId: ruleForm.ruleId.trim(),
        name: ruleForm.name.trim() || undefined,
        ruleType: ruleForm.ruleType,
        enabled: ruleForm.enabled,
        accountId: ruleForm.accountId.trim() || undefined,
        strategyId: ruleForm.strategyId.trim() || undefined,
        symbol: ruleForm.symbol.trim() || undefined,
        thresholdParams: ruleForm.thresholdParams.trim() || undefined,
        actionLevel: ruleForm.actionLevel,
        cooldownSec: ruleForm.cooldownSec ? Number(ruleForm.cooldownSec) : undefined,
      };
      const saved = await api.post<IntradayRiskRule>('/api/risk/intraday/rule', payload);
      setRuleMessage(`规则已保存：${saved.ruleId}`);
      rules.reload();
    } catch (err) {
      setRuleError(errorText(err));
    } finally {
      setSaving(false);
    }
  };

  const toggleRule = async (rule: IntradayRiskRule) => {
    setTogglingId(rule.ruleId);
    setRuleError(null);
    try {
      await api.post<string>(
        `/api/risk/intraday/rule/${encodeURIComponent(rule.ruleId)}/toggle`,
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

  const evaluateNow = async () => {
    setEvaluating(true);
    setDispositionError(null);
    setDispositionMessage(null);
    try {
      const logs = await api.post<RiskDispositionLog[]>('/api/risk/intraday/evaluate');
      setDispositionMessage(`全量评估完成，产生 ${logs?.length ?? 0} 条处置记录`);
      dispositions.reload();
      setTab('disposition');
    } catch (err) {
      setDispositionError(errorText(err));
    } finally {
      setEvaluating(false);
    }
  };

  const confirmDisposition = async (log: RiskDispositionLog) => {
    if (!log.dispositionId) {
      setDispositionError('处置记录缺少 dispositionId');
      return;
    }
    setConfirmingId(log.dispositionId);
    setDispositionError(null);
    setDispositionMessage(null);
    try {
      const { token } = await api.confirmToken();
      await api.post<RiskDispositionLog>(
        `/api/risk/intraday/disposition/${encodeURIComponent(log.dispositionId)}/confirm`,
        undefined,
        { token, params: { operator: operator.trim() || 'system' } },
      );
      setDispositionMessage(`强制平仓已确认：${log.dispositionId}`);
      dispositions.reload();
    } catch (err) {
      setDispositionError(errorText(err));
    } finally {
      setConfirmingId(null);
    }
  };

  const releaseBlock = async (scopeType: string, scopeId: string) => {
    if (!scopeType.trim() || !scopeId.trim()) {
      setBlockError('scopeType 与 scopeId 均不能为空');
      return;
    }
    setReleasing(true);
    setBlockError(null);
    try {
      await api.post<string>('/api/risk/intraday/block/release', undefined, {
        params: { scopeType: scopeType.trim(), scopeId: scopeId.trim() },
      });
      blocks.reload();
    } catch (err) {
      setBlockError(errorText(err));
    } finally {
      setReleasing(false);
    }
  };

  const releaseFromRow = (value: string) => {
    const separator = value.indexOf(':');
    if (separator <= 0) {
      setBlockError(`无法解析限制项作用域：${value}`);
      return;
    }
    releaseBlock(value.slice(0, separator), value.slice(separator + 1));
  };

  const ruleRows = rules.data ?? [];
  const enabledCount = ruleRows.filter((rule) => rule.enabled).length;
  const blockRows = (blocks.data ?? []).map((value) => ({ value }));

  return (
    <Page
      title="事中风控"
      subtitle="实时规则、账户风险状态、处置执行与限制开仓清单"
      actions={
        <Button variant="primary" onClick={evaluateNow} disabled={evaluating}>
          {evaluating ? '评估中…' : '手动全量评估'}
        </Button>
      }
    >
      <Grid columns={4}>
        <Stat label="规则总数" value={ruleRows.length} tone="info" />
        <Stat label="已启用规则" value={`${enabledCount}/${ruleRows.length}`} tone={enabledCount > 0 ? 'ok' : 'warn'} />
        <Stat label="处置记录" value={dispositions.data?.length ?? 0} tone="muted" />
        <Stat
          label="限制开仓"
          value={blockRows.length}
          tone={blockRows.length > 0 ? 'danger' : 'ok'}
        />
      </Grid>

      <Tabs tabs={TABS} active={tab} onChange={setTab} />

      {tab === 'rule' && (
        <>
          <Card title={ruleForm.ruleId ? `编辑规则：${ruleForm.ruleId}` : '新建规则'}>
            <Toolbar>
              <Field label="规则 ID" hint="唯一标识，必需">
                <Input
                  value={ruleForm.ruleId}
                  onChange={(event) => updateRule('ruleId', event.target.value)}
                  placeholder="IR-POS-001"
                />
              </Field>
              <Field label="规则名称">
                <Input
                  value={ruleForm.name}
                  onChange={(event) => updateRule('name', event.target.value)}
                  placeholder="单账户持仓上限"
                />
              </Field>
              <Field label="规则类型">
                <Select
                  value={ruleForm.ruleType}
                  onChange={(event) => updateRule('ruleType', event.target.value)}
                >
                  {RULE_TYPES.map((item) => (
                    <option key={item} value={item}>
                      {item}
                    </option>
                  ))}
                </Select>
              </Field>
              <Field label="动作级别">
                <Select
                  value={ruleForm.actionLevel}
                  onChange={(event) => updateRule('actionLevel', event.target.value)}
                >
                  {ACTION_LEVELS.map((item) => (
                    <option key={item} value={item}>
                      {item}
                    </option>
                  ))}
                </Select>
              </Field>
              <Field label="冷却秒数">
                <Input
                  value={ruleForm.cooldownSec}
                  onChange={(event) => updateRule('cooldownSec', event.target.value)}
                  placeholder="60"
                />
              </Field>
              <Field label="启用">
                <Select
                  value={ruleForm.enabled ? 'true' : 'false'}
                  onChange={(event) => updateRule('enabled', event.target.value === 'true')}
                >
                  <option value="true">启用</option>
                  <option value="false">停用</option>
                </Select>
              </Field>
              <Field label="适用账户">
                <Input
                  value={ruleForm.accountId}
                  onChange={(event) => updateRule('accountId', event.target.value)}
                  placeholder="空=全部"
                />
              </Field>
              <Field label="适用策略">
                <Input
                  value={ruleForm.strategyId}
                  onChange={(event) => updateRule('strategyId', event.target.value)}
                  placeholder="空=全部"
                />
              </Field>
              <Field label="适用标的">
                <Input
                  value={ruleForm.symbol}
                  onChange={(event) => updateRule('symbol', event.target.value)}
                  placeholder="空=全部"
                />
              </Field>
            </Toolbar>
            <Field label="阈值参数（JSON）" hint='如 {"maxPosition":100}'>
              <Textarea
                value={ruleForm.thresholdParams}
                onChange={(event) => updateRule('thresholdParams', event.target.value)}
                rows={3}
              />
            </Field>
            <Toolbar>
              <Button variant="primary" onClick={saveRule} disabled={saving}>
                {saving ? '保存中…' : '保存规则'}
              </Button>
              <Button onClick={resetRuleForm} disabled={saving}>
                清空表单
              </Button>
            </Toolbar>
            {ruleError && <Notice tone="danger">{ruleError}</Notice>}
            {ruleMessage && <Notice tone="ok">{ruleMessage}</Notice>}
          </Card>

          <Card title="规则列表">
            <AsyncState
              loading={rules.loading}
              error={rules.error}
              onRetry={rules.reload}
              empty={ruleRows.length === 0 && '暂无规则'}
            >
              <DataTable
                columns={[
                  { key: 'ruleId', title: '规则 ID' },
                  { key: 'name', title: '名称' },
                  { key: 'ruleType', title: '类型' },
                  {
                    key: 'actionLevel',
                    title: '动作',
                    render: (row) => <Badge tone={statusTone(row.actionLevel)}>{row.actionLevel}</Badge>,
                  },
                  { key: 'cooldownSec', title: '冷却(s)', align: 'right' },
                  {
                    key: 'enabled',
                    title: '启用',
                    render: (row) => (
                      <Badge tone={row.enabled ? 'ok' : 'muted'}>{row.enabled ? '启用' : '停用'}</Badge>
                    ),
                  },
                  { key: 'scopeId', title: '作用域', render: (row) => row.accountId || row.strategyId || row.symbol || '全部' },
                  {
                    key: 'actions',
                    title: '操作',
                    align: 'right',
                    render: (row) => (
                      <div style={{ display: 'flex', gap: 6, justifyContent: 'flex-end' }}>
                        <Button size="sm" onClick={() => editRule(row)}>
                          编辑
                        </Button>
                        <Button
                          size="sm"
                          variant={row.enabled ? 'danger' : 'primary'}
                          disabled={togglingId === row.ruleId}
                          onClick={() => toggleRule(row)}
                        >
                          {row.enabled ? '停用' : '启用'}
                        </Button>
                      </div>
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

      {tab === 'status' && (
        <Card title="账户实时风控状态">
          <Toolbar>
            <Field label="账户 ID">
              <Input
                value={statusAccount}
                onChange={(event) => setStatusAccount(event.target.value)}
                placeholder="ACC001"
              />
            </Field>
            <Button variant="primary" onClick={() => setCommittedAccount(statusAccount.trim())}>
              查询
            </Button>
          </Toolbar>
          <AsyncState
            loading={status.loading}
            error={status.error}
            onRetry={status.reload}
            empty={!committedAccount && '请输入账户 ID 后查询'}
          >
            <JsonBlock value={status.data} />
          </AsyncState>
        </Card>
      )}

      {tab === 'disposition' && (
        <>
          <Card title="处置记录">
            <Toolbar>
              <Field label="操作人" hint="强平确认时上报">
                <Input
                  value={operator}
                  onChange={(event) => setOperator(event.target.value)}
                  placeholder="risk_officer"
                />
              </Field>
            </Toolbar>
            {dispositionError && <Notice tone="danger">{dispositionError}</Notice>}
            {dispositionMessage && <Notice tone="ok">{dispositionMessage}</Notice>}
            <AsyncState
              loading={dispositions.loading}
              error={dispositions.error}
              onRetry={dispositions.reload}
              empty={(dispositions.data ?? []).length === 0 && '暂无处置记录'}
            >
              <DataTable
                columns={[
                  { key: 'dispositionId', title: '处置 ID' },
                  { key: 'triggerRule', title: '触发规则' },
                  { key: 'scopeId', title: '作用域', render: (row) => `${row.scopeType ?? '-'}:${row.scopeId ?? '-'}` },
                  {
                    key: 'action',
                    title: '动作',
                    render: (row) => <Badge tone={statusTone(row.action)}>{row.action}</Badge>,
                  },
                  {
                    key: 'result',
                    title: '结果',
                    render: (row) => <Badge tone={statusTone(row.result)}>{row.result}</Badge>,
                  },
                  { key: 'createTime', title: '时间' },
                  {
                    key: 'actions',
                    title: '操作',
                    align: 'right',
                    render: (row) =>
                      row.action === 'FORCE_CLOSE' && row.result === 'PENDING' ? (
                        <Button
                          size="sm"
                          variant="danger"
                          disabled={confirmingId === row.dispositionId}
                          onClick={() => confirmDisposition(row)}
                        >
                          {confirmingId === row.dispositionId ? '确认中…' : '确认强平'}
                        </Button>
                      ) : (
                        <span className="muted">-</span>
                      ),
                  },
                ]}
                rows={dispositions.data ?? []}
                rowKey={(row) => String(row.dispositionId ?? row.id)}
              />
            </AsyncState>
          </Card>

          <Card title="限制开仓清单">
            <Toolbar>
              <Field label="作用域类型">
                <Select
                  value={releaseScopeType}
                  onChange={(event) => setReleaseScopeType(event.target.value)}
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
                  value={releaseScopeId}
                  onChange={(event) => setReleaseScopeId(event.target.value)}
                  placeholder="ACC001"
                />
              </Field>
              <Button
                variant="primary"
                disabled={releasing}
                onClick={() => releaseBlock(releaseScopeType, releaseScopeId)}
              >
                解除限制
              </Button>
            </Toolbar>
            {blockError && <Notice tone="danger">{blockError}</Notice>}
            <AsyncState
              loading={blocks.loading}
              error={blocks.error}
              onRetry={blocks.reload}
              empty={blockRows.length === 0 && '当前无限制开仓项'}
            >
              <DataTable
                columns={[
                  { key: 'value', title: '作用域' },
                  {
                    key: 'actions',
                    title: '操作',
                    align: 'right',
                    render: (row) => (
                      <Button
                        size="sm"
                        variant="danger"
                        disabled={releasing}
                        onClick={() => releaseFromRow(row.value)}
                      >
                        解除
                      </Button>
                    ),
                  },
                ]}
                rows={blockRows}
                rowKey={(row) => String(row.value)}
              />
            </AsyncState>
          </Card>
        </>
      )}
    </Page>
  );
}
