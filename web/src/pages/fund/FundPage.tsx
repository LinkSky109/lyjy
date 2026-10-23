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
  Modal,
  Notice,
  Page,
  Select,
  Stat,
  Tabs,
  Toolbar,
  type Tone,
} from '../../components/ui';

/** 平台资金账户（/api/trade/platform/account） */
interface FundAccountView {
  accountId: string;
  accountName?: string;
  accountType?: string;
  currency?: string;
  totalAsset?: number;
  availableCash?: number;
  frozenCash?: number;
  margin?: number;
  unrealizedPnl?: number;
  realizedPnl?: number;
}

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

/** 账户划转（/api/fund/transfer） */
interface FundTransferView {
  transferNo?: string;
  fromAccountId?: string;
  toAccountId?: string;
  amount?: number;
  currency?: string;
  status?: string;
  operator?: string;
  remark?: string;
  fromBalanceBefore?: number;
  fromBalanceAfter?: number;
  toBalanceBefore?: number;
  toBalanceAfter?: number;
  createTime?: string;
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

interface PageResult<T> {
  items: T[];
  total: number;
  page: number;
  size: number;
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
  const [txAmountError, setTxAmountError] = useState<string | null>(null);
  const [txConfirmOpen, setTxConfirmOpen] = useState(false);
  const [accountQueryId, setAccountQueryId] = useState('DEFAULT-001');

  const accountView = useApi(
    () => api.get<FundAccountView>('/api/trade/platform/account', { accountId: accountQueryId }),
    [accountQueryId],
  );
  const [accountPage, setAccountPage] = useState(1);
  const ACCOUNT_PAGE_SIZE = 20;
  const accounts = useApi<PageResult<FundAccountView>>(
    () => api.get<PageResult<FundAccountView>>('/api/fund/accounts', {
      page: accountPage,
      size: ACCOUNT_PAGE_SIZE,
    }),
    [accountPage],
  );
  const accountTotal = accounts.data?.total ?? 0;
  const transactions = useApi<PageResult<CashTransactionView>>(
    () => api.get<PageResult<CashTransactionView>>('/api/fund/transactions', {
      status: 'PENDING',
      page: 1,
      size: 20,
    }),
    [],
  );

  const validateTransaction = (): boolean => {
    setTxAmountError(null);
    const account = txAccountId.trim();
    const amount = toNum(txAmount);
    if (!account) {
      setTxNotice({ tone: 'danger', text: '请填写账户 ID accountId' });
      return false;
    }
    if (amount === undefined || amount <= 0) {
      setTxAmountError('请填写大于 0 的金额，例如 100000。');
      return false;
    }
    if (accountView.data && accountView.data.accountId !== account) {
      setTxNotice({
        tone: 'danger',
        text: `账户数据尚未切换到 ${account}，请先刷新账户概览后再提交。`,
      });
      setAccountQueryId(account);
      accountView.reload();
      return false;
    }
    if (txType === 'WITHDRAW') {
      const available = accountView.data?.availableCash;
      if (available === undefined) {
        setTxNotice({
          tone: 'danger',
          text: '无法读取该账户的可用资金，暂不能出金。请先确认账户 ID 并刷新账户数据。',
        });
        return false;
      }
      if (amount > available) {
        setTxAmountError(`出金不得超过可用资金 ¥${fmt(available)}，请调整金额或先入金。`);
        return false;
      }
    }
    return true;
  };

  const submitTransaction = async () => {
    setTxNotice(null);
    if (!validateTransaction()) {
      setTxConfirmOpen(false);
      return;
    }
    const account = txAccountId.trim();
    const amount = toNum(txAmount) as number;
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
      setTxConfirmOpen(false);
      accountView.reload();
      accounts.reload();
      flows.reload();
      transactions.reload();
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
  const [approveConfirmOpen, setApproveConfirmOpen] = useState(false);

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
      setApproveConfirmOpen(false);
      accountView.reload();
      accounts.reload();
      flows.reload();
      transactions.reload();
    } catch (error) {
      setApproveNotice({ tone: 'danger', text: `审核失败：${errorText(error)}` });
    } finally {
      setApproveSubmitting(false);
    }
  };

  // ---------- 账户互转 ----------
  const [transferFrom, setTransferFrom] = useState('DEFAULT-001');
  const [transferTo, setTransferTo] = useState('');
  const [transferAmount, setTransferAmount] = useState('');
  const [transferCurrency, setTransferCurrency] = useState('CNY');
  const [transferOperator, setTransferOperator] = useState('operator');
  const [transferRemark, setTransferRemark] = useState('');
  const [transferKey, setTransferKey] = useState(() => `trf-${Date.now()}`);
  const [transferSubmitting, setTransferSubmitting] = useState(false);
  const [transferNotice, setTransferNotice] = useState<Notice>(null);
  const [transferResult, setTransferResult] = useState<FundTransferView | null>(null);
  const [transferConfirmOpen, setTransferConfirmOpen] = useState(false);
  const [transferAmountError, setTransferAmountError] = useState<string | null>(null);

  const submitTransfer = async () => {
    setTransferNotice(null);
    setTransferAmountError(null);
    const from = transferFrom.trim();
    const to = transferTo.trim();
    const amount = toNum(transferAmount);
    if (!from || !to) {
      setTransferNotice({ tone: 'danger', text: '请填写转出与转入账户。' });
      return;
    }
    if (from === to) {
      setTransferNotice({ tone: 'danger', text: '转出与转入账户不能相同。' });
      return;
    }
    if (amount === undefined || amount <= 0) {
      setTransferAmountError('划转金额必须大于 0。');
      return;
    }
    const source = (accounts.data?.items ?? []).find((item) => item.accountId === from);
    if (source?.availableCash !== undefined && amount > source.availableCash) {
      setTransferAmountError(`不得超过转出账户可用资金 ¥${fmt(source.availableCash)}。`);
      return;
    }
    setTransferSubmitting(true);
    try {
      const result = await api.post<FundTransferView>('/api/fund/transfer', {
        fromAccountId: from,
        toAccountId: to,
        amount,
        currency: transferCurrency.trim() || undefined,
        idempotencyKey: transferKey.trim() || undefined,
        operator: transferOperator.trim() || undefined,
        remark: transferRemark.trim() || undefined,
      });
      setTransferResult(result);
      setTransferNotice({
        tone: 'ok',
        text: `划转完成 ${result.transferNo ?? '-'}，状态 ${result.status ?? '-'}`,
      });
      setTransferConfirmOpen(false);
      setTransferKey(`trf-${Date.now()}`);
      accounts.reload();
      flows.reload();
    } catch (error) {
      setTransferNotice({ tone: 'danger', text: `划转失败：${errorText(error)}` });
    } finally {
      setTransferSubmitting(false);
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

  const [flowBizFilter, setFlowBizFilter] = useState('');
  const [flowPage, setFlowPage] = useState(1);
  const FLOW_PAGE_SIZE = 20;
  const flows = useApi<PageResult<FundFlowView>>(
    () =>
      api.get<PageResult<FundFlowView>>('/api/fund/flows', {
        accountId: flowQuery.accountId,
        start: toIso(flowQuery.start),
        end: toIso(flowQuery.end),
        bizType: flowBizFilter || undefined,
        page: flowPage,
        size: FLOW_PAGE_SIZE,
      }),
    [flowQuery.accountId, flowQuery.start, flowQuery.end, flowBizFilter, flowPage],
  );

  const applyFlowQuery = () => {
    setFlowPage(1);
    setFlowQuery({
      accountId: flowAccountDraft.trim(),
      start: flowStartDraft,
      end: flowEndDraft,
    });
  };

  const flowRows = flows.data?.items ?? [];
  const flowBizTypes = Array.from(
    new Set(flowRows.map((row) => row.bizType).filter(Boolean)),
  ) as string[];
  const flowTotal = flows.data?.total ?? 0;
  const flowPages = Math.max(1, Math.ceil(flowTotal / FLOW_PAGE_SIZE));

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
      <Card
        title="账户概览"
        padded={false}
        actions={
          <>
            <span className="muted">共 {accounts.data?.total ?? 0} 个账户</span>
            <Button size="sm" variant="ghost" onClick={() => accounts.reload()}>
              刷新
            </Button>
          </>
        }
      >
        <AsyncState
          loading={accounts.loading}
          error={accounts.error}
          onRetry={accounts.reload}
          empty={!accounts.loading && (accounts.data?.items ?? []).length === 0 && '暂无资金账户。'}
        >
          {(accounts.data?.items ?? []).length > 0 && (
            <DataTable
              columns={[
                { key: 'accountId', title: '账户' },
                { key: 'accountName', title: '名称' },
                { key: 'accountType', title: '类型' },
                { key: 'currency', title: '币种' },
                {
                  key: 'totalAsset',
                  title: '总资产',
                  align: 'right',
                  render: (row: FundAccountView) => fmt(row.totalAsset),
                },
                {
                  key: 'availableCash',
                  title: '可用资金',
                  align: 'right',
                  render: (row: FundAccountView) => fmt(row.availableCash),
                },
                {
                  key: 'frozenCash',
                  title: '冻结资金',
                  align: 'right',
                  render: (row: FundAccountView) => fmt(row.frozenCash),
                },
                {
                  key: 'margin',
                  title: '保证金',
                  align: 'right',
                  render: (row: FundAccountView) => fmt(row.margin),
                },
              ]}
              rows={accounts.data?.items ?? []}
              rowKey={(row: FundAccountView) => row.accountId}
              empty="暂无账户数据"
            />
          )}
        </AsyncState>
        {accountTotal > ACCOUNT_PAGE_SIZE && (
          <div className="card-body">
            <Toolbar>
              <span className="muted">
                第 {accountPage} / {Math.max(1, Math.ceil(accountTotal / ACCOUNT_PAGE_SIZE))} 页 · 共 {accountTotal} 个
              </span>
              <Button size="sm" disabled={accountPage <= 1} onClick={() => setAccountPage((v) => Math.max(1, v - 1))}>
                上一页
              </Button>
              <Button
                size="sm"
                disabled={accountPage >= Math.ceil(accountTotal / ACCOUNT_PAGE_SIZE)}
                onClick={() => setAccountPage((v) => v + 1)}
              >
                下一页
              </Button>
            </Toolbar>
          </div>
        )}
      </Card>

      <Card>
        <Tabs
          active={tab}
          onChange={setTab}
          tabs={[
            { key: 'transaction', label: '出入金' },
            { key: 'transfer', label: '账户互转' },
            { key: 'flows', label: '流水' },
            { key: 'reconcile', label: '对账' },
          ]}
        />

        {tab === 'transaction' && (
          <div className="stack-sm">
            {txNotice && <Notice tone={txNotice.tone} live>{txNotice.text}</Notice>}
            <Grid columns={3}>
              <Field label="账户 ID accountId">
                <Input
                  value={txAccountId}
                  onChange={(event) => setTxAccountId(event.target.value)}
                  onBlur={() => setAccountQueryId(txAccountId.trim() || 'DEFAULT-001')}
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
              <Field label="金额 amount" error={txAmountError ?? undefined}>
                <Input
                  value={txAmount}
                  onChange={(event) => {
                    setTxAmount(event.target.value);
                    setTxAmountError(null);
                  }}
                  placeholder="100000"
                  inputMode="decimal"
                  aria-invalid={txAmountError ? 'true' : undefined}
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
              <Button
                variant="primary"
                onClick={() => {
                  setTxNotice(null);
                  setAccountQueryId(txAccountId.trim() || 'DEFAULT-001');
                  if (validateTransaction()) {
                    setTxConfirmOpen(true);
                  }
                }}
                disabled={txSubmitting}
              >
                {txSubmitting ? '提交中…' : '提交出入金申请'}
              </Button>
              <span className="muted">出金不得超过账户可用资金；提交前需二次确认。</span>
            </Toolbar>

            {txResult && (
              <Card title="最近申请结果">
                <TransactionFields tx={txResult} />
                <JsonBlock value={txResult} />
              </Card>
            )}

            <Card
              title="待审批队列"
              padded={false}
              actions={
                <>
                  <span className="muted">{transactions.data?.total ?? 0} 笔待处理</span>
                  <Button size="sm" variant="ghost" onClick={transactions.reload}>
                    刷新
                  </Button>
                </>
              }
            >
              <AsyncState
                loading={transactions.loading}
                error={transactions.error}
                onRetry={transactions.reload}
                empty={!transactions.loading && (transactions.data?.items ?? []).length === 0 && '暂无待审批出入金。'}
              >
                <DataTable
                  columns={[
                    { key: 'createTime', title: '申请时间' },
                    { key: 'transactionNo', title: '流水号' },
                    { key: 'accountId', title: '账户' },
                    { key: 'type', title: '类型' },
                    { key: 'amount', title: '金额', align: 'right', render: (row: CashTransactionView) => fmt(row.amount) },
                    { key: 'applicant', title: '申请人' },
                    { key: 'status', title: '状态', render: (row: CashTransactionView) => <Badge tone={row.status === 'PENDING' ? 'warn' : 'muted'}>{row.status ?? '-'}</Badge> },
                    {
                      key: 'action',
                      title: '操作',
                      align: 'right',
                      render: (row: CashTransactionView) => (
                        <Button
                          size="sm"
                          onClick={() => {
                            setApproveNo(row.transactionNo ?? '');
                            setApproved('true');
                            setApproveConfirmOpen(true);
                          }}
                        >
                          审核
                        </Button>
                      ),
                    },
                  ]}
                  rows={transactions.data?.items ?? []}
                  rowKey={(row: CashTransactionView) => row.transactionNo ?? String(row.id ?? '')}
                  empty="暂无待审批记录"
                />
              </AsyncState>
            </Card>

            <Card title="出入金审核">
              {approveNotice && <Notice tone={approveNotice.tone} live>{approveNotice.text}</Notice>}
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
                  variant={approved === 'true' ? 'default' : 'danger'}
                  onClick={() => setApproveConfirmOpen(true)}
                  disabled={approveSubmitting}
                >
                  {approveSubmitting ? '提交中…' : '提交审核'}
                </Button>
              </Toolbar>
              {approveResult && (
                <div className="mt-3">
                  <TransactionFields tx={approveResult} />
                  <TransactionTimeline tx={approveResult} />
                  <JsonBlock value={approveResult} />
                </div>
              )}
            </Card>
          </div>
        )}

        {tab === 'transfer' && (
          <div className="stack-sm">
            {transferNotice && <Notice tone={transferNotice.tone} live>{transferNotice.text}</Notice>}
            <Grid columns={3}>
              <Field label="转出账户 fromAccountId">
                <Select value={transferFrom} onChange={(event) => setTransferFrom(event.target.value)}>
                  <option value="">请选择</option>
                  {(accounts.data?.items ?? []).map((item) => (
                    <option key={item.accountId} value={item.accountId}>
                      {item.accountId}（可用 {fmt(item.availableCash)}）
                    </option>
                  ))}
                </Select>
              </Field>
              <Field label="转入账户 toAccountId">
                <Select value={transferTo} onChange={(event) => setTransferTo(event.target.value)}>
                  <option value="">请选择</option>
                  {(accounts.data?.items ?? []).map((item) => (
                    <option key={item.accountId} value={item.accountId}>
                      {item.accountId}（可用 {fmt(item.availableCash)}）
                    </option>
                  ))}
                </Select>
              </Field>
              <Field label="金额 amount" error={transferAmountError ?? undefined}>
                <Input
                  value={transferAmount}
                  onChange={(event) => {
                    setTransferAmount(event.target.value);
                    setTransferAmountError(null);
                  }}
                  placeholder="10000"
                  inputMode="decimal"
                />
              </Field>
              <Field label="币种 currency">
                <Input value={transferCurrency} onChange={(event) => setTransferCurrency(event.target.value)} />
              </Field>
              <Field label="操作人 operator">
                <Input value={transferOperator} onChange={(event) => setTransferOperator(event.target.value)} />
              </Field>
              <Field label="备注 remark（可选）">
                <Input value={transferRemark} onChange={(event) => setTransferRemark(event.target.value)} />
              </Field>
            </Grid>
            <Field label="幂等键 idempotencyKey" hint="重复提交相同幂等键只执行一次">
              <Input value={transferKey} onChange={(event) => setTransferKey(event.target.value)} />
            </Field>
            <Toolbar>
              <Button
                variant="primary"
                onClick={() => {
                  setTransferNotice(null);
                  setTransferAmountError(null);
                  setTransferConfirmOpen(true);
                }}
                disabled={transferSubmitting}
              >
                {transferSubmitting ? '划转中…' : '提交划转'}
              </Button>
              <Button onClick={() => setTransferKey(`trf-${Date.now()}`)}>重新生成幂等键</Button>
              <span className="muted">划转在同一事务内更新双方余额并写双边流水。</span>
            </Toolbar>
            {transferResult && (
              <Card title="最近划转结果">
                <KeyValue
                  items={[
                    { label: 'transferNo', value: transferResult.transferNo ?? '-' },
                    { label: '状态', value: <Badge tone={transferResult.status === 'COMPLETED' ? 'ok' : 'warn'}>{transferResult.status ?? '-'}</Badge> },
                    { label: '转出', value: transferResult.fromAccountId ?? '-' },
                    { label: '转入', value: transferResult.toAccountId ?? '-' },
                    { label: '金额', value: fmt(transferResult.amount) },
                    { label: '转出余额', value: `${fmt(transferResult.fromBalanceBefore)} → ${fmt(transferResult.fromBalanceAfter)}` },
                    { label: '转入余额', value: `${fmt(transferResult.toBalanceBefore)} → ${fmt(transferResult.toBalanceAfter)}` },
                  ]}
                />
                <JsonBlock value={transferResult} />
              </Card>
            )}
          </div>
        )}

        {tab === 'flows' && (
          <div className="stack-sm">
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
              <Field label="业务类型筛选">
                <Select
                  value={flowBizFilter}
                  onChange={(event) => {
                    setFlowBizFilter(event.target.value);
                    setFlowPage(1);
                  }}
                >
                  <option value="">全部</option>
                  {flowBizTypes.map((item) => (
                    <option key={item} value={item}>
                      {item}
                    </option>
                  ))}
                </Select>
              </Field>
              <Button onClick={applyFlowQuery}>查询流水</Button>
              <Button onClick={flows.reload}>刷新</Button>
            </Toolbar>

            <AsyncState
              loading={flows.loading}
              error={flows.error}
              onRetry={flows.reload}
              empty={
                !flows.loading &&
                flowRows.length === 0 &&
                '所选区间没有资金流水。请放宽时间范围或确认该账户已发生出入金/成交。'
              }
            >
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
                      <span className={(row.changeAmount ?? 0) >= 0 ? 'delta-up' : 'delta-down'}>
                        {fmt(row.changeAmount)}
                      </span>
                    ),
                  },
                  { key: 'balanceBefore', title: '变动前', align: 'right', render: (row: FundFlowView) => fmt(row.balanceBefore) },
                  { key: 'balanceAfter', title: '变动后', align: 'right', render: (row: FundFlowView) => fmt(row.balanceAfter) },
                  { key: 'createTime', title: '时间' },
                  { key: 'remark', title: '备注' },
                ]}
                rows={flowRows}
                rowKey={(row: FundFlowView) => row.flowNo ?? String(row.id ?? '')}
                empty="暂无流水"
              />
            </AsyncState>
            <Toolbar>
              <span className="muted">
                第 {flowPage} / {flowPages} 页 · 共 {flowTotal} 条
              </span>
              <Button size="sm" disabled={flowPage <= 1} onClick={() => setFlowPage((v) => Math.max(1, v - 1))}>
                上一页
              </Button>
              <Button size="sm" disabled={flowPage >= flowPages} onClick={() => setFlowPage((v) => Math.min(flowPages, v + 1))}>
                下一页
              </Button>
            </Toolbar>
          </div>
        )}

        {tab === 'reconcile' && (
          <div className="stack-sm">
            {reconNotice && <Notice tone={reconNotice.tone} live>{reconNotice.text}</Notice>}
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

      <Modal
        open={transferConfirmOpen}
        title="确认账户划转"
        onClose={() => setTransferConfirmOpen(false)}
        footer={
          <>
            <Button onClick={() => setTransferConfirmOpen(false)} disabled={transferSubmitting}>
              取消
            </Button>
            <Button variant="primary" onClick={submitTransfer} disabled={transferSubmitting}>
              {transferSubmitting ? '划转中…' : '确认划转'}
            </Button>
          </>
        }
      >
        <Notice tone="warn">划转将即时更新双方可用资金，请核对账户与金额。</Notice>
        <KeyValue
          items={[
            { label: '转出', value: transferFrom || '-' },
            { label: '转入', value: transferTo || '-' },
            { label: '金额', value: `${fmt(toNum(transferAmount))} ${transferCurrency}` },
            { label: '操作人', value: transferOperator || '-' },
          ]}
        />
      </Modal>

      <Modal
        open={txConfirmOpen}
        title="确认出入金申请"
        onClose={() => setTxConfirmOpen(false)}
        footer={
          <>
            <Button onClick={() => setTxConfirmOpen(false)} disabled={txSubmitting}>
              取消
            </Button>
            <Button variant="primary" onClick={submitTransaction} disabled={txSubmitting}>
              {txSubmitting ? '提交中…' : '确认提交'}
            </Button>
          </>
        }
      >
        {txType === 'WITHDRAW' && (
          <Notice tone="warn">
            出金不得超过可用资金（当前 ¥{fmt(accountView.data?.availableCash)}），提交后需审核。
          </Notice>
        )}
        <KeyValue
          items={[
            { label: '账户', value: txAccountId.trim() || '-' },
            { label: '类型', value: txType === 'DEPOSIT' ? '入金' : '出金' },
            { label: '金额', value: `¥${fmt(toNum(txAmount))}` },
            { label: '币种', value: 'CNY' },
            { label: '银行账户', value: txBankAccount.trim() || '-' },
            { label: '申请人', value: txApplicant.trim() || '-' },
          ]}
        />
      </Modal>

      <Modal
        open={approveConfirmOpen}
        title="确认出入金审核"
        onClose={() => setApproveConfirmOpen(false)}
        footer={
          <>
            <Button onClick={() => setApproveConfirmOpen(false)} disabled={approveSubmitting}>
              取消
            </Button>
            <Button
              variant={approved === 'true' ? 'primary' : 'danger'}
              onClick={submitApprove}
              disabled={approveSubmitting}
            >
              {approveSubmitting ? '提交中…' : '确认审核'}
            </Button>
          </>
        }
      >
        <Notice tone="warn">
          审核结果不可撤销；通过后将更新账户资金，驳回则终止该笔申请。
        </Notice>
        <KeyValue
          items={[
            { label: '流水号', value: approveNo.trim() || '-' },
            { label: '审核人', value: approver.trim() || '-' },
            { label: '审核结论', value: approved === 'true' ? '通过' : '拒绝' },
          ]}
        />
      </Modal>
    </Page>
  );
}

function TransactionTimeline({ tx }: { tx: CashTransactionView }) {
  const steps = [
    { label: '申请', at: tx.createTime, detail: tx.applicant ?? '-', tone: 'info' as Tone },
    { label: '审核', at: tx.approveTime, detail: tx.approver ?? '-', tone: tx.status === 'REJECTED' ? 'danger' as Tone : 'ok' as Tone },
    { label: '完成', at: tx.completeTime, detail: tx.status ?? '-', tone: tx.status === 'COMPLETED' ? 'ok' as Tone : 'muted' as Tone },
  ].filter((step) => step.at);
  if (steps.length === 0) {
    return null;
  }
  return (
    <ol className="timeline">
      {steps.map((step) => (
        <li className="timeline-item" key={step.label}>
          <span className={`timeline-dot dot-${step.tone}`} aria-hidden="true" />
          <div>
            <div className="timeline-title">
              {step.label}
              <Badge tone={step.tone}>{step.detail}</Badge>
            </div>
            <div className="timeline-time">{step.at}</div>
          </div>
        </li>
      ))}
    </ol>
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
