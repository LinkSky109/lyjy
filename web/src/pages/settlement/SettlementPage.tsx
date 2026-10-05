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
  Input,
  JsonBlock,
  KeyValue,
  Modal,
  Notice,
  Page,
  Select,
  Tabs,
  Textarea,
  Toolbar,
  statusTone,
  type Column,
} from '../../components/ui';

interface SettlementBatch {
  batchId: string;
  tradeDate?: string;
  status?: string;
  accountCount?: number;
  totalPnl?: number | string;
  totalFee?: number | string;
  totalDiff?: number;
  startedAt?: string;
  finishedAt?: string;
  errorMessage?: string;
}

interface SettlementDiffItem {
  diffId: string;
  batchId?: string;
  accountId?: string;
  fieldName?: string;
  systemValue?: number | string;
  counterValue?: number | string;
  diffValue?: number | string;
  tolerance?: number | string;
  status?: string;
  adjustBy?: string;
  adjustNote?: string;
}

interface ReportResult {
  batchId?: string;
  type?: string;
  data?: unknown;
}

const REPORT_TYPES = [
  { key: 'pnl', label: '盈亏' },
  { key: 'fund', label: '资金' },
  { key: 'position', label: '持仓' },
  { key: 'trade', label: '成交' },
];

export default function SettlementPage() {
  const [tab, setTab] = useState('batches');
  const [busy, setBusy] = useState(false);
  const [actionError, setActionError] = useState<string | null>(null);
  const [okMsg, setOkMsg] = useState<string | null>(null);

  const [tradeDate, setTradeDate] = useState('');
  const [force, setForce] = useState(false);
  const [selectedBatchId, setSelectedBatchId] = useState('');

  const [adjustDiff, setAdjustDiff] = useState<SettlementDiffItem | null>(null);
  const [adjustBy, setAdjustBy] = useState('');
  const [adjustNote, setAdjustNote] = useState('');

  const [reportType, setReportType] = useState('pnl');

  const batches = useApi(
    () => api.get<SettlementBatch[]>('/api/settlement/batches', tradeDate ? { tradeDate } : undefined),
    [tradeDate],
  );
  const batch = useApi<SettlementBatch | null>(
    () =>
      selectedBatchId
        ? api.get<SettlementBatch>(`/api/settlement/batch/${selectedBatchId}`)
        : Promise.resolve(null),
    [selectedBatchId],
  );
  const diffs = useApi<SettlementDiffItem[] | null>(
    () =>
      selectedBatchId
        ? api.get<SettlementDiffItem[]>(`/api/settlement/batch/${selectedBatchId}/diffs`)
        : Promise.resolve(null),
    [selectedBatchId],
  );
  const report = useApi<ReportResult | null>(
    () =>
      selectedBatchId
        ? api.get<ReportResult>(`/api/settlement/report/${selectedBatchId}`, { type: reportType })
        : Promise.resolve(null),
    [selectedBatchId, reportType],
  );

  const run = async (fn: () => Promise<void>) => {
    setBusy(true);
    setActionError(null);
    setOkMsg(null);
    try {
      await fn();
    } catch (error) {
      setActionError(errorText(error));
    } finally {
      setBusy(false);
    }
  };

  const runSettlement = () =>
    run(async () => {
      const params = { tradeDate: tradeDate || undefined, force: force ? true : undefined };
      if (force) {
        const { token } = await api.confirmToken();
        const result = await api.post<SettlementBatch>('/api/settlement/run', undefined, { token, params });
        setOkMsg(`已重跑清算：${result.batchId}（${result.status ?? '-'}）`);
      } else {
        const result = await api.post<SettlementBatch>('/api/settlement/run', undefined, { params });
        setOkMsg(`清算完成：${result.batchId}（${result.status ?? '-'}，差异 ${result.totalDiff ?? 0}）`);
      }
      batches.reload();
    });

  const submitAdjust = () =>
    run(async () => {
      if (!adjustDiff) {
        return;
      }
      if (!adjustBy.trim()) {
        throw new Error('调账人不能为空');
      }
      await api.post<SettlementDiffItem>(
        `/api/settlement/diff/${adjustDiff.diffId}/adjust`,
        undefined,
        { params: { adjustBy: adjustBy.trim(), note: adjustNote.trim() || undefined } },
      );
      setOkMsg(`差异工单 ${adjustDiff.diffId} 已调账`);
      setAdjustDiff(null);
      setAdjustBy('');
      setAdjustNote('');
      diffs.reload();
    });

  const closeBatch = () =>
    run(async () => {
      if (!selectedBatchId) {
        throw new Error('请先选择批次');
      }
      const { token } = await api.confirmToken();
      const result = await api.post<SettlementBatch>(
        `/api/settlement/batch/${selectedBatchId}/close`,
        undefined,
        { token },
      );
      setOkMsg(`批次 ${result.batchId} 已关闭`);
      batch.reload();
      batches.reload();
    });

  const batchColumns: Column[] = [
    { key: 'batchId', title: '批次 ID' },
    { key: 'tradeDate', title: '交易日' },
    {
      key: 'status',
      title: '状态',
      render: (row) => <Badge tone={statusTone(row.status)}>{row.status ?? '-'}</Badge>,
    },
    { key: 'accountCount', title: '账户数', align: 'right' },
    { key: 'totalPnl', title: '总盈亏', align: 'right' },
    { key: 'totalFee', title: '总费用', align: 'right' },
    { key: 'totalDiff', title: '差异数', align: 'right' },
    {
      key: 'actions',
      title: '操作',
      render: (row) => (
        <Button
          size="sm"
          variant={row.batchId === selectedBatchId ? 'primary' : 'default'}
          onClick={() => {
            setSelectedBatchId(row.batchId);
            setTab('diffs');
          }}
        >
          {row.batchId === selectedBatchId ? '已选' : '关注'}
        </Button>
      ),
    },
  ];

  const diffColumns: Column[] = [
    { key: 'diffId', title: '工单 ID' },
    { key: 'accountId', title: '账户' },
    { key: 'fieldName', title: '字段' },
    { key: 'systemValue', title: '系统值', align: 'right' },
    { key: 'counterValue', title: '柜台值', align: 'right' },
    { key: 'diffValue', title: '差值', align: 'right' },
    { key: 'tolerance', title: '容忍度', align: 'right' },
    {
      key: 'status',
      title: '状态',
      render: (row) => <Badge tone={statusTone(row.status)}>{row.status ?? '-'}</Badge>,
    },
    { key: 'adjustBy', title: '调账人' },
    {
      key: 'actions',
      title: '操作',
      render: (row) =>
        row.status === 'CLOSED' ? (
          <span className="muted">已关闭</span>
        ) : (
          <Button size="sm" onClick={() => setAdjustDiff(row)} disabled={busy}>
            调账
          </Button>
        ),
    },
  ];

  const reportData = report.data?.data;
  const reportColumns: Column[] =
    Array.isArray(reportData) && reportData.length > 0
      ? Object.keys(reportData[0] as Record<string, unknown>).map((key) => ({ key, title: key }))
      : [];

  return (
    <Page title="日终清算与对账" subtitle="清算批次、差异工单处理与多类型报表">
      <Tabs
        tabs={[
          { key: 'batches', label: '批次' },
          { key: 'diffs', label: '差异' },
          { key: 'report', label: '报表' },
        ]}
        active={tab}
        onChange={setTab}
      />

      {actionError && <Notice tone="danger">{actionError}</Notice>}
      {okMsg && <Notice tone="ok">{okMsg}</Notice>}

      {tab === 'batches' && (
        <>
          <Card title="运行清算">
            <Toolbar>
              <Field label="交易日" hint="YYYY-MM-DD，留空按当日">
                <Input type="date" value={tradeDate} onChange={(event) => setTradeDate(event.target.value)} />
              </Field>
              <Field label="重跑（force）" hint="需一次性确认令牌">
                <Select value={force ? 'true' : 'false'} onChange={(event) => setForce(event.target.value === 'true')}>
                  <option value="false">否</option>
                  <option value="true">是</option>
                </Select>
              </Field>
              <Button variant="primary" disabled={busy} onClick={runSettlement}>
                运行清算
              </Button>
            </Toolbar>
            <Notice tone="warn">
              同一交易日已有批次时，必须勾选「重跑」并携带确认令牌；已关闭的批次禁止重跑。
            </Notice>
          </Card>

          <Card title="批次列表" actions={<Button size="sm" disabled={busy} onClick={() => batches.reload()}>刷新</Button>}>
            <AsyncState
              loading={batches.loading}
              error={batches.error}
              onRetry={batches.reload}
              empty={!batches.data || batches.data.length === 0 ? '暂无清算批次' : false}
            >
              <DataTable columns={batchColumns} rows={batches.data} rowKey={(row) => row.batchId} />
            </AsyncState>
          </Card>

          <Card title="批次详情">
            {!selectedBatchId ? (
              <div className="empty">请在批次列表点击「关注」选择批次</div>
            ) : (
              <AsyncState loading={batch.loading} error={batch.error} onRetry={batch.reload}>
                {batch.data && (
                  <KeyValue
                    items={[
                      { label: '批次 ID', value: batch.data.batchId },
                      { label: '交易日', value: batch.data.tradeDate ?? '-' },
                      {
                        label: '状态',
                        value: <Badge tone={statusTone(batch.data.status)}>{batch.data.status ?? '-'}</Badge>,
                      },
                      { label: '账户数', value: batch.data.accountCount ?? '-' },
                      { label: '总盈亏', value: batch.data.totalPnl ?? '-' },
                      { label: '总费用', value: batch.data.totalFee ?? '-' },
                      { label: '差异数', value: batch.data.totalDiff ?? '-' },
                      { label: '开始时间', value: batch.data.startedAt ?? '-' },
                      { label: '结束时间', value: batch.data.finishedAt ?? '-' },
                      { label: '错误信息', value: batch.data.errorMessage ?? '-' },
                    ]}
                  />
                )}
                <div style={{ marginTop: 12 }}>
                  <Notice tone="warn">关闭批次需一次性确认令牌，且要求所有差异工单均已处理。</Notice>
                  <div style={{ marginTop: 12 }}>
                    <Button
                      variant="danger"
                      disabled={busy || !selectedBatchId || batch.data?.status === 'CLOSED'}
                      onClick={closeBatch}
                    >
                      关闭批次
                    </Button>
                  </div>
                </div>
              </AsyncState>
            )}
          </Card>
        </>
      )}

      {tab === 'diffs' && (
        <Card
          title={`差异工单${selectedBatchId ? ` · ${selectedBatchId}` : ''}`}
          actions={
            <Button size="sm" disabled={busy || !selectedBatchId} onClick={() => diffs.reload()}>
              刷新
            </Button>
          }
        >
          {!selectedBatchId ? (
            <div className="empty">请先在「批次」页签选择批次</div>
          ) : (
            <AsyncState
              loading={diffs.loading}
              error={diffs.error}
              onRetry={diffs.reload}
              empty={!diffs.data || diffs.data.length === 0 ? '该批次无差异工单' : false}
            >
              <DataTable columns={diffColumns} rows={diffs.data} rowKey={(row) => row.diffId} />
            </AsyncState>
          )}
        </Card>
      )}

      {tab === 'report' && (
        <Card title="清算报表" actions={<Button size="sm" disabled={busy || !selectedBatchId} onClick={() => report.reload()}>刷新</Button>}>
          <Toolbar>
            <Field label="批次">
              <Select value={selectedBatchId} onChange={(event) => setSelectedBatchId(event.target.value)}>
                <option value="">请选择批次</option>
                {(batches.data ?? []).map((item) => (
                  <option key={item.batchId} value={item.batchId}>
                    {item.batchId}（{item.tradeDate ?? '-'}）
                  </option>
                ))}
              </Select>
            </Field>
          </Toolbar>

          <div style={{ marginTop: 12 }}>
            <Tabs tabs={REPORT_TYPES} active={reportType} onChange={setReportType} />
          </div>

          {!selectedBatchId ? (
            <div className="empty">请选择批次查看报表</div>
          ) : (
            <AsyncState loading={report.loading} error={report.error} onRetry={report.reload}>
              {Array.isArray(reportData) ? (
                <DataTable columns={reportColumns} rows={reportData as any[]} empty="报表无数据" />
              ) : (
                <JsonBlock value={reportData ?? {}} />
              )}
            </AsyncState>
          )}
        </Card>
      )}

      <Modal
        open={adjustDiff !== null}
        title={`差异调账${adjustDiff ? ` · ${adjustDiff.diffId}` : ''}`}
        onClose={() => setAdjustDiff(null)}
        footer={
          <>
            <Button onClick={() => setAdjustDiff(null)} disabled={busy}>
              取消
            </Button>
            <Button variant="primary" onClick={submitAdjust} disabled={busy}>
              提交调账
            </Button>
          </>
        }
      >
        {adjustDiff && (
          <KeyValue
            items={[
              { label: '账户', value: adjustDiff.accountId ?? '-' },
              { label: '字段', value: adjustDiff.fieldName ?? '-' },
              { label: '差值', value: adjustDiff.diffValue ?? '-' },
              { label: '容忍度', value: adjustDiff.tolerance ?? '-' },
            ]}
          />
        )}
        <Field label="调账人（adjustBy）" hint="必填">
          <Input value={adjustBy} onChange={(event) => setAdjustBy(event.target.value)} placeholder="risk_officer" />
        </Field>
        <Field label="调账说明（note，可选）">
          <Textarea value={adjustNote} onChange={(event) => setAdjustNote(event.target.value)} />
        </Field>
      </Modal>
    </Page>
  );
}
