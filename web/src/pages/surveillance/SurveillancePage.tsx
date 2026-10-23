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
  Modal,
  Notice,
  Page,
  Select,
  Stat,
  statusTone,
  Textarea,
  Toolbar,
} from '../../components/ui';

interface AbnormalTradeAlert {
  id?: number;
  alertId: string;
  ruleCode?: string;
  accountId?: string;
  strategyId?: string;
  symbol?: string;
  score?: number;
  detailJson?: string;
  severity?: string;
  status?: string;
  handler?: string;
  handleNote?: string;
  createTime?: string;
  updateTime?: string;
}

type ActionMode = 'confirm' | 'close';

const STATUS_OPTIONS = ['', 'OPEN', 'CONFIRMED', 'CLOSED'];

export default function SurveillancePage() {
  const [statusFilter, setStatusFilter] = useState('OPEN');
  const [accountFilter, setAccountFilter] = useState('');

  const alerts = useApi(
    () =>
      api.get<AbnormalTradeAlert[]>('/api/surveillance/abnormal/list', {
        status: statusFilter || undefined,
        accountId: accountFilter.trim() || undefined,
      }),
    [statusFilter, accountFilter],
  );

  const rules = useApi(
    () => api.get<Record<string, number>>('/api/surveillance/abnormal/rules'),
    [],
  );

  const [detail, setDetail] = useState<AbnormalTradeAlert | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);
  const [detailError, setDetailError] = useState<string | null>(null);
  const [detailOpen, setDetailOpen] = useState(false);

  const [actionMode, setActionMode] = useState<ActionMode>('confirm');
  const [actionTarget, setActionTarget] = useState<AbnormalTradeAlert | null>(null);
  const [handler, setHandler] = useState('compliance');
  const [note, setNote] = useState('');
  const [actionError, setActionError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const [scanWindow, setScanWindow] = useState('300');
  const [scanning, setScanning] = useState(false);
  const [scanMessage, setScanMessage] = useState<string | null>(null);
  const [scanError, setScanError] = useState<string | null>(null);

  const openDetail = async (alert: AbnormalTradeAlert) => {
    setDetailOpen(true);
    setDetail(alert);
    setDetailError(null);
    setDetailLoading(true);
    try {
      const data = await api.get<AbnormalTradeAlert>(
        `/api/surveillance/abnormal/${encodeURIComponent(alert.alertId)}`,
      );
      setDetail(data);
    } catch (err) {
      setDetailError(errorText(err));
    } finally {
      setDetailLoading(false);
    }
  };

  const openAction = (mode: ActionMode, alert: AbnormalTradeAlert) => {
    setActionMode(mode);
    setActionTarget(alert);
    setActionError(null);
    setNote('');
  };

  const closeAction = () => {
    setActionTarget(null);
    setActionError(null);
  };

  const submitAction = async () => {
    if (!actionTarget) {
      return;
    }
    setSubmitting(true);
    setActionError(null);
    try {
      const { token } = await api.confirmToken();
      await api.post<AbnormalTradeAlert>(
        `/api/surveillance/abnormal/${encodeURIComponent(actionTarget.alertId)}/${actionMode}`,
        undefined,
        { token, params: { handler: handler.trim() || 'system', note: note.trim() } },
      );
      closeAction();
      alerts.reload();
      setDetailOpen(false);
    } catch (err) {
      setActionError(errorText(err));
    } finally {
      setSubmitting(false);
    }
  };

  const scan = async () => {
    setScanning(true);
    setScanError(null);
    setScanMessage(null);
    try {
      const found = await api.post<AbnormalTradeAlert[]>('/api/surveillance/abnormal/scan', undefined, {
        params: { windowSec: scanWindow ? Number(scanWindow) : 300 },
      });
      setScanMessage(`扫描完成，命中 ${found?.length ?? 0} 条告警`);
      alerts.reload();
    } catch (err) {
      setScanError(errorText(err));
    } finally {
      setScanning(false);
    }
  };

  const rows = alerts.data ?? [];
  const openCount = rows.filter((row) => row.status === 'OPEN').length;
  const criticalCount = rows.filter((row) => row.severity === 'CRITICAL').length;
  const ruleEntries = Object.entries(rules.data ?? {}).map(([code, value]) => ({ code, value }));

  return (
    <Page title="异常交易监控" subtitle="异常交易识别、规则阈值与敏感处置（确认上报/关闭需二次确认令牌）">
      <Grid columns={3}>
        <Stat label="当前列表告警" value={rows.length} tone="info" />
        <Stat label="未处置" value={openCount} tone={openCount > 0 ? 'warn' : 'ok'} />
        <Stat label="严重告警" value={criticalCount} tone={criticalCount > 0 ? 'danger' : 'ok'} />
      </Grid>

      <Card title="告警列表">
        <Toolbar>
          <Field label="状态">
            <Select value={statusFilter} onChange={(event) => setStatusFilter(event.target.value)}>
              {STATUS_OPTIONS.map((item) => (
                <option key={item || 'ALL'} value={item}>
                  {item || '全部'}
                </option>
              ))}
            </Select>
          </Field>
          <Field label="账户 ID">
            <Input
              value={accountFilter}
              onChange={(event) => setAccountFilter(event.target.value)}
              placeholder="可选"
            />
          </Field>
          <Button onClick={alerts.reload}>刷新</Button>
          <Field label="扫描窗口（秒）">
            <Input
              value={scanWindow}
              onChange={(event) => setScanWindow(event.target.value)}
              placeholder="300"
            />
          </Field>
          <Button variant="primary" onClick={scan} disabled={scanning}>
            {scanning ? '扫描中…' : '手动扫描'}
          </Button>
        </Toolbar>
        {scanError && <Notice tone="danger" live>{scanError}</Notice>}
        {scanMessage && <Notice tone="ok" live>{scanMessage}</Notice>}
        <AsyncState
          loading={alerts.loading}
          error={alerts.error}
          onRetry={alerts.reload}
          empty={rows.length === 0 && '暂无告警'}
        >
          <DataTable
            columns={[
              { key: 'alertId', title: '告警号' },
              { key: 'ruleCode', title: '规则' },
              { key: 'accountId', title: '账户' },
              { key: 'symbol', title: '标的' },
              { key: 'score', title: '评分', align: 'right' },
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
              { key: 'createTime', title: '时间' },
              {
                key: 'actions',
                title: '操作',
                align: 'right',
                render: (row) => (
                  <Button size="sm" onClick={() => openDetail(row)}>
                    处理
                  </Button>
                ),
              },
            ]}
            rows={rows}
            rowKey={(row) => String(row.alertId)}
          />
        </AsyncState>
      </Card>

      <Card title="监控规则阈值">
        <AsyncState
          loading={rules.loading}
          error={rules.error}
          onRetry={rules.reload}
          empty={ruleEntries.length === 0 && '暂无规则'}
        >
          <DataTable
            columns={[
              { key: 'code', title: '规则编码' },
              { key: 'value', title: '阈值', align: 'right' },
            ]}
            rows={ruleEntries}
            rowKey={(row) => String(row.code)}
          />
        </AsyncState>
      </Card>

      <Modal
        open={detailOpen}
        title={`告警详情${detail?.alertId ? `：${detail.alertId}` : ''}`}
        onClose={() => setDetailOpen(false)}
        wide
        footer={
          detail ? (
            <>
              <Button onClick={() => setDetailOpen(false)}>关闭</Button>
              {detail.status === 'OPEN' && (
                <Button
                  variant="primary"
                  onClick={() => {
                    setDetailOpen(false);
                    openAction('confirm', detail);
                  }}
                >
                  确认上报
                </Button>
              )}
              {detail.status !== 'CLOSED' && (
                <Button
                  variant="danger"
                  onClick={() => {
                    setDetailOpen(false);
                    openAction('close', detail);
                  }}
                >
                  关闭告警
                </Button>
              )}
            </>
          ) : undefined
        }
      >
        <AsyncState loading={detailLoading} error={detailError} onRetry={() => detail && openDetail(detail)}>
          {detail && (
            <>
              <div style={{ marginBottom: 8 }}>
                <Badge tone={statusTone(detail.severity)}>{detail.severity ?? '-'}</Badge>
                <Badge tone={statusTone(detail.status)}>{detail.status ?? '-'}</Badge>
                <Badge tone="muted">{detail.ruleCode ?? '-'}</Badge>
              </div>
              <JsonBlock value={detail.detailJson ?? detail} />
            </>
          )}
        </AsyncState>
      </Modal>

      <Modal
        open={actionTarget !== null}
        title={actionMode === 'confirm' ? '确认上报告警' : '关闭告警'}
        onClose={closeAction}
        footer={
          <div style={{ display: 'flex', gap: 8, justifyContent: 'flex-end' }}>
            <Button onClick={closeAction} disabled={submitting}>
              取消
            </Button>
            <Button
              variant={actionMode === 'confirm' ? 'primary' : 'danger'}
              onClick={submitAction}
              disabled={submitting}
            >
              {submitting ? '提交中…' : actionMode === 'confirm' ? '确认上报' : '关闭告警'}
            </Button>
          </div>
        }
      >
        <Notice tone="warn">该操作需一次性确认令牌（30 秒内有效）。</Notice>
        {actionTarget && <p className="muted">目标告警：{actionTarget.alertId}</p>}
        <Field label="处置人">
          <Input value={handler} onChange={(event) => setHandler(event.target.value)} placeholder="compliance" />
        </Field>
        <Field label="处置说明">
          <Textarea value={note} onChange={(event) => setNote(event.target.value)} rows={3} />
        </Field>
        {actionError && <Notice tone="danger" live>{actionError}</Notice>}
      </Modal>
    </Page>
  );
}
