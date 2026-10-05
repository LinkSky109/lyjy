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
  KeyValue,
  Notice,
  Page,
  Select,
  Stat,
  Tabs,
  Toolbar,
  type Tone,
} from '../../components/ui';

/** 出入金申请（/api/fund/transaction） */
interface CashTransactionView {
  id?: number;
  transactionNo?: string;
  accountId?: string;
  type?: string;
  amount?: number;
  currency?: string;
  status?: string;
  bankAccount?: string;
  applicant?: string;
  approver?: string;
  remark?: string;
  createTime?: string;
  approveTime?: string;
  completeTime?: string;
}

/** 资金流水（/api/fund/flows） */
interface FundFlowView {
  id?: number;
  flowNo?: string;
  accountId?: string;
  bizType?: string;
  bizNo?: string;
  changeAmount?: number;
  balanceBefore?: number;
  balanceAfter?: number;
  symbol?: string;
  remark?: string;
  createTime?: string;
}

/** 资金对账结果（/api/fund/reconcile） */
interface FundReconciliationView {
  id?: number;
  reconNo?: string;
  accountId?: string;
  reconDate?: string;
  systemBalance?: number;
  gatewayBalance?: number;
  systemAvailable?: number;
  gatewayAvailable?: number;
  systemMargin?: number;
  gatewayMargin?: number;
  difference?: number;
  status?: string;
  differenceReason?: string;
  createTime?: string;
}

type Notice = { tone: Tone; text: string } | null;

const TRANSACTION_TYPES = ['DEPOSIT', 'WITHDRAW'];

function pad(value: number): string {
  return String(value).padStart(2, '0');
}

function localDateTime(date: Date, withTime: boolean): string {
  const day = `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
  return withTime ? `${day}T${pad(date.getHours())}:${pad(date.getMinutes())}` : `${day}T00:00`;
}

/** datetime-local 值补齐为 ISO LocalDateTime（yyyy-MM-ddTHH:mm:ss） */
function toIso(value: string): string | undefined {
  const text = value.trim();
  if (text === '') {
    return undefined;
  }
  return text.length === 16 ? `${text}:00` : text;
}

function toNum(value: string): number | undefined {
  const text = value.trim();
  if (text === '') {
    return undefined;
  }
  const parsed = Number(text);
  return Number.isFinite(parsed) ? parsed : undefined;
}

function fmt(value: number | undefined | null): string {
  if (value === undefined || value === null) {
    return '-';
  }
  return value.toLocaleString('zh-CN', { maximumFractionDigits: 4 });
}

const GATEWAY_FIELDS: Array<{ key: string; label: string }> = [
  { key: 'available', label: '可用资金 available' },
  { key: 'frozen', label: '冻结资金 frozen' },
  { key: 'balance', label: '总资金 balance' },
  { key: 'margin', label: '保证金 margin' },
  { key: 'positionPnl', label: '浮动盈亏 positionPnl' },
  { key: 'closePnl', label: '平仓盈亏 closePnl' },
  { key: 'commission', label: '手续费 commission' },
  { key: 'riskDegree', label: '风险度 riskDegree' },
];

export default function FundPage() {
  const [tab, setTab] = useState('transaction');

  // ---------- 出入金申请 ----------
  const [txAccountId, setTxAccountId] = useState('DEFAULT-001');
  const [txType, setTxType] = useState('DEPOSIT');
  const [txAmount, setTxAmount] = useState('');
  const [txBankAccount, setTxBankAccount] = useState('');
  const [txApplicant, setTxApplicant] = useState('');
  const [txRemark, setTxRemark] = useState('');
  const [txSubmitting, setTxSubmitting] = useState(false);
  const [txNotice, setTxNotice] = useState<Notice>(null);
  const [txResult, setTxResult] = useState<CashTransactionView | null>(null);

  const submitTransaction = async () => {
    setTxNotice(null);
    const account = txAccountId.trim();
    const amount = toNum(txAmount);
    if (!account) {
      setTxNotice({ tone: 'danger', text: '请填写账户 ID accountId' });
      return;
    }
    if (amount === undefined || amount <= 0) {
      setTxNotice({ tone: 'danger', text: '请填写大于 0 的金额 amount' });
      return;
    }
    setTxSubmitting(true);
    try {
      const result = await api.post<CashTransactionView>('/api/fund/transaction', {
        accountId: account,
        type: txType,
        amount,
        currency: 'CNY',
        bankAccount: txBankAccount.trim() || undefined,
        applicant: txApplicant.trim() || undefined,
        remark: txRemark.trim() || undefined,
      });
      setTxResult(result);
      setTxNotice({
        tone: 'ok',
        text: `申请已提交 流水号=${result.transactionNo ?? '-'} 状态=${result.status ?? '-'}`,
      });
    } catch (error) {
      setTxNotice({ tone: 'danger', text: `提交失败：${errorText(error)}` });
    } finally {
      setTxSubmitting(false);
    }
  };

  // ---------- 出入金审核 ----------
  const [approveNo, setApproveNo] = useState('');
  const [approver, setApprover] = useState('');
  const [approved, setApproved] = useState('true');
  const [approveSubmitting, setApproveSubmitting] = useState(false);
  const [approveNotice, setApproveNotice] = useState<Notice>(null);
  const [approveResult, setApproveResult] = useState<CashTransactionView | null>(null);

  const submitApprove = async () => {
    setApproveNotice(null);
    const no = approveNo.trim();
    if (!no) {
      setApproveNotice({ tone: 'danger', text: '请填写出入金流水号 transactionNo' });
      return;
    }
    const operator = approver.trim();
    if (!operator) {
      setApproveNotice({ tone: 'danger', text: '请填写审核人 approver' });
      return;
    }
    setApproveSubmitting(true);
    try {
      const result = await api.put<CashTransactionView>(
        `/api/fund/transaction/${encodeURIComponent(no)}/approve`,
        undefined,
        { params: { approver: operator, approved: approved === 'true' } },
      );
      setApproveResult(result);
      setApproveNotice({
        tone: 'ok',
        text: `审核完成 流水号=${result.transactionNo ?? no} 状态=${result.status ?? '-'}`,
      });
    } catch (error) {
      setApproveNotice({ tone: 'danger', text: `审核失败：${errorText(error)}` });
    } finally {
      setApproveSubmitting(false);
    }
  };

  // ---------- 流水查询 ----------
  const now = new Date();
  const [flowAccountDraft, setFlowAccountDraft] = useState('DEFAULT-001');
  const [flowStartDraft, setFlowStartDraft] = useState(() => localDateTime(now, false));
  const [flowEndDraft, setFlowEndDraft] = useState(() => localDateTime(now, true));
  const [flowQuery, setFlowQuery] = useState(() => ({
    accountId: 'DEFAULT-001',
    start: localDateTime(now, false),
    end: localDateTime(now, true),
  }));

  const flows = useApi(
    () =>
      api.get<FundFlowView[]>('/api/fund/flows', {
        accountId: flowQuery.accountId,
        start: toIso(flowQuery.start),
        end: toIso(flowQuery.end),
      }),
    [flowQuery.accountId, flowQuery.start, flowQuery.end],
  );

  const applyFlowQuery = () => {
    setFlowQuery({
      accountId: flowAccountDraft.trim(),
      start: flowStartDraft,
      end: flowEndDraft,
    });
  };

  // ---------- 资金对账 ----------
  const [reconAccountId, setReconAccountId] = useState('DEFAULT-001');
  const [gatewayValues, setGatewayValues] = useState<Record<string, string>>({});
  const [reconSubmitting, setReconSubmitting] = useState(false);
  const [reconNotice, setReconNotice] = useState<Notice>(null);
  const [reconResult, setReconResult] = useState<FundReconciliationView | null>(null);

  const setGatewayValue = (key: string, value: string) => {
    setGatewayValues((prev) => ({ ...prev, [key]: value }));
  };

  const submitReconcile = async () => {
    setReconNotice(null);
    const account = reconAccountId.trim();
    if (!account) {
      setReconNotice({ tone: 'danger', text: '请填写账户 ID accountId' });
      return;
    }
    const payload: Record<string, unknown> = { accountId: account };
    GATEWAY_FIELDS.forEach(({ key }) => {
      const parsed = toNum(gatewayValues[key] ?? '');
      if (parsed !== undefined) {
        payload[key] = parsed;
      }
    });
    setReconSubmitting(true);
    try {
      const result = await api.post<FundReconciliationView>('/api/fund/reconcile', payload, {
        params: { accountId: account },
      });
      setReconResult(result);
      setReconNotice({
        tone: 'ok',
        text: `对账完成 批次=${result.reconNo ?? '-'} 状态=${result.status ?? '-'} 差异=${fmt(result.difference)}`,
      });
    } catch (error) {
      setReconNotice({ tone: 'danger', text: `对账失败：${errorText(error)}` });
    } finally {
      setReconSubmitting(false);
    }
  };

  return (
    <Page title="资金管理" subtitle="出入金申请审核、资金流水查询与柜台对账">
      <Card>
        <Tabs
          active={tab}
          onChange={setTab}
          tabs={[
            { key: 'transaction', label: '出入金' },
            { key: 'flows', label: '流水' },
            { key: 'reconcile', label: '对账' },
          ]}
        />

        {tab === 'transaction' && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
            {txNotice && <Notice tone={txNotice.tone}>{txNotice.text}</Notice>}
            <Grid columns={3}>
              <Field label="账户 ID accountId">
                <Input
                  value={txAccountId}
                  onChange={(event) => setTxAccountId(event.target.value)}
                  placeholder="DEFAULT-001"
                />
              </Field>
              <Field label="类型 type">
                <Select value={txType} onChange={(event) => setTxType(event.target.value)}>
                  {TRANSACTION_TYPES.map((item) => (
                    <option key={item} value={item}>
                      {item}
                    </option>
                  ))}
                </Select>
              </Field>
              <Field label="金额 amount">
                <Input
                  value={txAmount}
                  onChange={(event) => setTxAmount(event.target.value)}
                  placeholder="100000"
                />
              </Field>
              <Field label="银行账户 bankAccount（可选）">
                <Input
                  value={txBankAccount}
                  onChange={(event) => setTxBankAccount(event.target.value)}
                  placeholder="6222 **** 0000"
                />
              </Field>
              <Field label="申请人 applicant（可选）">
                <Input
                  value={txApplicant}
                  onChange={(event) => setTxApplicant(event.target.value)}
                  placeholder="operator"
                />
              </Field>
              <Field label="备注 remark（可选）">
                <Input
                  value={txRemark}
                  onChange={(event) => setTxRemark(event.target.value)}
                  placeholder="补充说明"
                />
              </Field>
            </Grid>
            <Toolbar>
              <Button variant="primary" onClick={submitTransaction} disabled={txSubmitting}>
                {txSubmitting ? '提交中…' : '提交出入金申请'}
              </Button>
            </Toolbar>

            {txResult && (
              <Card title="最近申请结果">
                <TransactionFields tx={txResult} />
                <JsonBlock value={txResult} />
              </Card>
            )}

            <Card title="出入金审核">
              {approveNotice && <Notice tone={approveNotice.tone}>{approveNotice.text}</Notice>}
              <Toolbar>
                <Field label="流水号 transactionNo">
                  <Input
                    value={approveNo}
                    onChange={(event) => setApproveNo(event.target.value)}
                    placeholder="TXN..."
                  />
                </Field>
                <Field label="审核人 approver">
                  <Input
                    value={approver}
                    onChange={(event) => setApprover(event.target.value)}
                    placeholder="fund_manager"
                  />
                </Field>
                <Field label="审核结论 approved">
                  <Select value={approved} onChange={(event) => setApproved(event.target.value)}>
                    <option value="true">通过（true）</option>
                    <option value="false">拒绝（false）</option>
                  </Select>
                </Field>
                <Button
                  variant={approved === 'true' ? 'primary' : 'danger'}
                  onClick={submitApprove}
                  disabled={approveSubmitting}
                >
                  {approveSubmitting ? '提交中…' : '提交审核'}
                </Button>
              </Toolbar>
              {approveResult && (
                <div style={{ marginTop: 12 }}>
                  <TransactionFields tx={approveResult} />
                  <JsonBlock value={approveResult} />
                </div>
              )}
            </Card>
          </div>
        )}

        {tab === 'flows' && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
            <Toolbar>
              <Field label="账户 ID accountId">
                <Input
                  value={flowAccountDraft}
                  onChange={(event) => setFlowAccountDraft(event.target.value)}
                  placeholder="DEFAULT-001"
                />
              </Field>
              <Field label="开始时间 start">
                <Input
                  type="datetime-local"
                  value={flowStartDraft}
                  onChange={(event) => setFlowStartDraft(event.target.value)}
                />
              </Field>
              <Field label="结束时间 end">
                <Input
                  type="datetime-local"
                  value={flowEndDraft}
                  onChange={(event) => setFlowEndDraft(event.target.value)}
                />
              </Field>
              <Button variant="primary" onClick={applyFlowQuery}>
                查询流水
              </Button>
              <Button onClick={flows.reload}>刷新</Button>
            </Toolbar>

            <AsyncState loading={flows.loading} error={flows.error} onRetry={flows.reload}>
              <DataTable
                columns={[
                  { key: 'flowNo', title: '流水号' },
                  {
                    key: 'bizType',
                    title: '业务类型',
                    render: (row: FundFlowView) => <Badge tone={statusToneOf(row.bizType)}>{row.bizType ?? '-'}</Badge>,
                  },
                  { key: 'bizNo', title: '关联单号' },
                  { key: 'symbol', title: '标的' },
                  {
                    key: 'changeAmount',
                    title: '变动金额',
                    align: 'right',
                    render: (row: FundFlowView) => (
                      <span style={{ color: (row.changeAmount ?? 0) >= 0 ? 'var(--ok)' : 'var(--danger)' }}>
                        {fmt(row.changeAmount)}
                      </span>
                    ),
                  },
                  { key: 'balanceBefore', title: '变动前', align: 'right', render: (row: FundFlowView) => fmt(row.balanceBefore) },
                  { key: 'balanceAfter', title: '变动后', align: 'right', render: (row: FundFlowView) => fmt(row.balanceAfter) },
                  { key: 'createTime', title: '时间' },
                  { key: 'remark', title: '备注' },
                ]}
                rows={flows.data ?? []}
                rowKey={(row: FundFlowView) => row.flowNo ?? String(row.id ?? '')}
                empty="暂无流水"
              />
            </AsyncState>
          </div>
        )}

        {tab === 'reconcile' && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
            {reconNotice && <Notice tone={reconNotice.tone}>{reconNotice.text}</Notice>}
            <Field label="账户 ID accountId">
              <Input
                value={reconAccountId}
                onChange={(event) => setReconAccountId(event.target.value)}
                placeholder="DEFAULT-001"
              />
            </Field>
            <Grid columns={4}>
              {GATEWAY_FIELDS.map(({ key, label }) => (
                <Field key={key} label={label}>
                  <Input
                    value={gatewayValues[key] ?? ''}
                    onChange={(event) => setGatewayValue(key, event.target.value)}
                    placeholder="留空则该字段不参与对账"
                  />
                </Field>
              ))}
            </Grid>
            <Toolbar>
              <Button variant="primary" onClick={submitReconcile} disabled={reconSubmitting}>
                {reconSubmitting ? '对账中…' : '执行资金对账'}
              </Button>
            </Toolbar>

            {reconResult && (
              <Card title="对账结果">
                <Grid columns={3}>
                  <Stat
                    label="对账状态 status"
                    value={reconResult.status ?? '-'}
                    tone={reconResult.status === 'MATCHED' ? 'ok' : 'danger'}
                  />
                  <Stat
                    label="差异金额 difference"
                    value={fmt(reconResult.difference)}
                    tone={(reconResult.difference ?? 0) === 0 ? 'ok' : 'danger'}
                  />
                  <Stat label="对账批次 reconNo" value={reconResult.reconNo ?? '-'} tone="info" />
                  <Stat label="系统账面 systemBalance" value={fmt(reconResult.systemBalance)} tone="muted" />
                  <Stat label="柜台账面 gatewayBalance" value={fmt(reconResult.gatewayBalance)} tone="muted" />
                  <Stat label="系统可用 systemAvailable" value={fmt(reconResult.systemAvailable)} tone="muted" />
                  <Stat label="柜台可用 gatewayAvailable" value={fmt(reconResult.gatewayAvailable)} tone="muted" />
                  <Stat label="系统保证金 systemMargin" value={fmt(reconResult.systemMargin)} tone="muted" />
                  <Stat label="柜台保证金 gatewayMargin" value={fmt(reconResult.gatewayMargin)} tone="muted" />
                </Grid>
                <KeyValue
                  items={[
                    { label: 'accountId', value: reconResult.accountId ?? '-' },
                    { label: 'reconDate', value: reconResult.reconDate ?? '-' },
                    { label: 'createTime', value: reconResult.createTime ?? '-' },
                    { label: 'differenceReason', value: reconResult.differenceReason ?? '-' },
                  ]}
                />
                <JsonBlock value={reconResult} />
              </Card>
            )}
          </div>
        )}
      </Card>
    </Page>
  );
}

function TransactionFields({ tx }: { tx: CashTransactionView }) {
  return (
    <KeyValue
      items={[
        { label: 'transactionNo', value: tx.transactionNo ?? '-' },
        { label: 'accountId', value: tx.accountId ?? '-' },
        { label: 'type', value: tx.type ?? '-' },
        { label: 'amount', value: fmt(tx.amount) },
        { label: 'currency', value: tx.currency ?? '-' },
        { label: 'status', value: <Badge tone={statusToneOf(tx.status)}>{tx.status ?? '-'}</Badge> },
        { label: 'applicant', value: tx.applicant ?? '-' },
        { label: 'approver', value: tx.approver ?? '-' },
        { label: 'bankAccount', value: tx.bankAccount ?? '-' },
        { label: 'remark', value: tx.remark ?? '-' },
        { label: 'createTime', value: tx.createTime ?? '-' },
        { label: 'approveTime', value: tx.approveTime ?? '-' },
      ]}
    />
  );
}

/** 状态/业务类型到 Tone 的映射 */
function statusToneOf(value: string | undefined): Tone {
  const text = (value ?? '').toUpperCase();
  if (
    ['COMPLETED', 'APPROVED', 'MATCHED', 'SUCCESS', 'DEPOSIT', 'MARGIN_RELEASE', 'REALIZED_PNL'].includes(text)
  ) {
    return 'ok';
  }
  if (['PENDING', 'REVIEWING', 'WITHDRAW', 'MARGIN_FREEZE', 'MARGIN_SETTLE', 'COMMISSION'].includes(text)) {
    return 'warn';
  }
  if (['REJECTED', 'FAILED', 'MISMATCHED', 'ERROR', 'CANCELED', 'CANCELLED'].includes(text)) {
    return 'danger';
  }
  return 'muted';
}
