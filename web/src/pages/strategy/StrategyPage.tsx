import { useEffect, useState } from 'react';
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

interface SandboxStatus {
  strategyId?: string;
  status?: string;
  pid?: number;
  startedAt?: string;
  finishedAt?: string;
  exitCode?: number;
  outputTail?: string;
}

interface StrategyAuditLog {
  id?: number;
  strategyId?: string;
  action?: string;
  operator?: string;
  detailJson?: string;
  ipAddr?: string;
  createTime?: string;
}

interface StrategyCodeResult {
  strategyId?: string;
  version?: number;
  checksum?: string;
  sourceCode?: string;
}

const AUDIT_ACTIONS = [
  { key: '', label: '全部' },
  { key: 'UPLOAD', label: 'UPLOAD' },
  { key: 'DOWNLOAD', label: 'DOWNLOAD' },
  { key: 'RUN', label: 'RUN' },
  { key: 'STOP', label: 'STOP' },
  { key: 'EDIT_PARAM', label: 'EDIT_PARAM' },
];

export default function StrategyPage() {
  const [tab, setTab] = useState('upload');
  const [busy, setBusy] = useState(false);
  const [actionError, setActionError] = useState<string | null>(null);
  const [okMsg, setOkMsg] = useState<string | null>(null);
  const [autoRefresh, setAutoRefresh] = useState(false);
  const [watchdogOn, setWatchdogOn] = useState(false);

  const [strategyId, setStrategyId] = useState('');
  const [codeName, setCodeName] = useState('');
  const [sourceCode, setSourceCode] = useState('');
  const [downloaded, setDownloaded] = useState<StrategyCodeResult | null>(null);
  const [action, setAction] = useState('');

  const status = useApi<SandboxStatus | null>(
    () =>
      strategyId.trim()
        ? api.get<SandboxStatus>(`/api/strategy/sandbox/status/${strategyId.trim()}`)
        : Promise.resolve(null),
    [strategyId],
  );
  const audit = useApi(
    () =>
      api.get<StrategyAuditLog[]>('/api/strategy/audit', {
        strategyId: strategyId.trim() || undefined,
        action: action || undefined,
      }),
    [strategyId, action],
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

  // 运行监控自动轮询：打开后每 3s 刷新沙箱状态与输出尾部
  useEffect(() => {
    if (!autoRefresh || !strategyId.trim()) {
      return;
    }
    const timer = setInterval(() => status.reload(), 3000);
    return () => clearInterval(timer);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [autoRefresh, strategyId]);

  const requireStrategyId = (): string => {
    const id = strategyId.trim();
    if (!id) {
      throw new Error('请先填写策略 ID');
    }
    return id;
  };

  const uploadCode = () =>
    run(async () => {
      const id = requireStrategyId();
      if (!sourceCode.trim()) {
        throw new Error('策略源码不能为空');
      }
      const result = await api.post<StrategyCodeResult>(`/api/strategy/${id}/code`, {
        name: codeName.trim() || id,
        sourceCode,
      });
      setOkMsg(`源码已加密上传：版本 ${result.version ?? '-'}`);
      status.reload();
      audit.reload();
    });

  const downloadCode = () =>
    run(async () => {
      const id = requireStrategyId();
      const { token } = await api.confirmToken();
      const result = await api.post<StrategyCodeResult>(
        `/api/strategy/${id}/code/download`,
        undefined,
        { token },
      );
      setDownloaded(result);
      setOkMsg('源码已解密返回，请在受控环境下查阅');
      audit.reload();
    });

  const runStrategy = () =>
    run(async () => {
      const id = requireStrategyId();
      const result = await api.post<SandboxStatus>(`/api/strategy/${id}/run`);
      setOkMsg(`策略已启动，状态：${result.status ?? '-'}`);
      status.reload();
      audit.reload();
    });

  const toggleWatchdog = () =>
    run(async () => {
      const id = requireStrategyId();
      const next = !watchdogOn;
      const result = await api.post<{ enabled?: boolean }>(
        `/api/strategy/${id}/watchdog`,
        undefined,
        { params: { enabled: next, checkIntervalMs: 30000 } },
      );
      setWatchdogOn(result.enabled ?? next);
      setOkMsg(`自动值守已${result.enabled ?? next ? '开启' : '关闭'}：策略离线时每 30s 自动拉起`);
    });

  const stopStrategy = () =>
    run(async () => {
      const id = requireStrategyId();
      const result = await api.post<SandboxStatus>(`/api/strategy/${id}/stop`);
      setOkMsg(`策略已停止，状态：${result.status ?? '-'}`);
      status.reload();
      audit.reload();
    });

  const auditColumns: Column[] = [
    { key: 'createTime', title: '时间' },
    { key: 'strategyId', title: '策略 ID' },
    {
      key: 'action',
      title: '动作',
      render: (row) => <Badge tone={statusTone(row.action)}>{row.action ?? '-'}</Badge>,
    },
    { key: 'operator', title: '操作人' },
    { key: 'ipAddr', title: '来源 IP' },
    { key: 'detailJson', title: '详情' },
  ];

  return (
    <Page title="策略加密与沙箱" subtitle="源码加密上传/下载、沙箱运行与操作审计">
      <Card title="策略上下文">
        <Toolbar>
          <Field label="策略 ID（strategyId）">
            <Input value={strategyId} onChange={(event) => setStrategyId(event.target.value)} placeholder="STG-001" />
          </Field>
          <Field label="沙箱状态" hint={status.error ?? undefined}>
            {status.data?.status ? (
              <Badge tone={statusTone(status.data.status)}>{status.data.status}</Badge>
            ) : (
              <span className="muted">未运行</span>
            )}
          </Field>
        </Toolbar>
      </Card>

      <Tabs
        tabs={[
          { key: 'upload', label: '上传' },
          { key: 'runtime', label: '运行' },
          { key: 'audit', label: '审计' },
        ]}
        active={tab}
        onChange={setTab}
      />

      {actionError && <Notice tone="danger">{actionError}</Notice>}
      {okMsg && <Notice tone="ok">{okMsg}</Notice>}

      {tab === 'upload' && (
        <Card title="上传 / 下载策略源码">
          <Field label="策略名称（name）" hint="缺省使用策略 ID">
            <Input value={codeName} onChange={(event) => setCodeName(event.target.value)} />
          </Field>
          <Field label="源码（sourceCode）" hint="明文仅在服务端进程内流转，落库前加密">
            <Textarea value={sourceCode} onChange={(event) => setSourceCode(event.target.value)} placeholder="# Python 策略源码" />
          </Field>
          <Toolbar>
            <Button variant="primary" disabled={busy} onClick={uploadCode}>
              加密上传
            </Button>
            <Button variant="danger" disabled={busy} onClick={downloadCode}>
              下载源码（敏感）
            </Button>
          </Toolbar>
          <Notice tone="warn">
            下载会解密返回明文源码并写入 DOWNLOAD 审计，需一次性确认令牌。
          </Notice>
        </Card>
      )}

      {tab === 'runtime' && (
        <Card
          title="沙箱运行控制"
          actions={
            <Toolbar>
              <label className="muted" style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}>
                <input
                  type="checkbox"
                  checked={autoRefresh}
                  onChange={(event) => setAutoRefresh(event.target.checked)}
                  disabled={!strategyId.trim()}
                />
                自动刷新（3s）
              </label>
              <Button size="sm" disabled={busy || !strategyId.trim()} onClick={() => status.reload()}>
                刷新状态
              </Button>
            </Toolbar>
          }
        >
          <Toolbar>
            <Button variant="primary" disabled={busy} onClick={runStrategy}>
              启动策略
            </Button>
            <Button variant="danger" disabled={busy} onClick={stopStrategy}>
              停止策略
            </Button>
            <Button variant="default" size="sm" disabled={busy || !strategyId.trim()} onClick={toggleWatchdog}>
              {watchdogOn ? '关闭自动值守' : '开启自动值守'}
            </Button>
          </Toolbar>
          <div className="muted" style={{ marginTop: 6 }}>
            自动值守：策略离线时定时自动拉起（挂机运行），开启后将自动启动并以每 30s 检查。
          </div>

          <div style={{ marginTop: 16 }}>
            {!strategyId.trim() ? (
              <div className="empty">请在上方填写策略 ID</div>
            ) : (
              <AsyncState loading={status.loading} error={status.error} onRetry={status.reload}>
                <KeyValue
                  items={[
                    { label: '策略 ID', value: status.data?.strategyId ?? strategyId },
                    {
                      label: '状态',
                      value: <Badge tone={statusTone(status.data?.status)}>{status.data?.status ?? 'OFFLINE'}</Badge>,
                    },
                    { label: 'PID', value: status.data?.pid ?? '-' },
                    { label: '开始时间', value: status.data?.startedAt ?? '-' },
                    { label: '结束时间', value: status.data?.finishedAt ?? '-' },
                    { label: '退出码', value: status.data?.exitCode ?? '-' },
                  ]}
                />
                <h3 style={{ margin: '16px 0 8px', fontSize: 14 }}>输出尾部（outputTail）</h3>
                <JsonBlock value={status.data?.outputTail ?? '（无输出）'} />
              </AsyncState>
            )}
          </div>
        </Card>
      )}

      {tab === 'audit' && (
        <Card title="操作审计" actions={<Button size="sm" disabled={busy} onClick={() => audit.reload()}>刷新</Button>}>
          <Toolbar>
            <Field label="动作筛选">
              <Select value={action} onChange={(event) => setAction(event.target.value)}>
                {AUDIT_ACTIONS.map((item) => (
                  <option key={item.key || 'ALL'} value={item.key}>
                    {item.label}
                  </option>
                ))}
              </Select>
            </Field>
          </Toolbar>
          <AsyncState
            loading={audit.loading}
            error={audit.error}
            onRetry={audit.reload}
            empty={!audit.data || audit.data.length === 0 ? '暂无审计记录' : false}
          >
            <DataTable columns={auditColumns} rows={audit.data} rowKey={(row, index) => String(row.id ?? index)} />
          </AsyncState>
        </Card>
      )}

      <Modal
        open={downloaded !== null}
        title="策略源码（敏感）"
        wide
        onClose={() => setDownloaded(null)}
        footer={
          <Button onClick={() => setDownloaded(null)} disabled={busy}>
            关闭
          </Button>
        }
      >
        <Notice tone="danger">
          以下为解密后的明文策略源码，请勿截图、转发或粘贴到不受控环境；关闭后页面不再保留。
        </Notice>
        {downloaded && (
          <div style={{ marginTop: 12 }}>
            <KeyValue
              items={[
                { label: '策略 ID', value: downloaded.strategyId ?? '-' },
                { label: '版本', value: downloaded.version ?? '-' },
                { label: '校验和', value: downloaded.checksum ?? '-' },
              ]}
            />
          </div>
        )}
        <h3 style={{ margin: '16px 0 8px', fontSize: 14 }}>源码</h3>
        <JsonBlock value={downloaded?.sourceCode ?? ''} />
      </Modal>
    </Page>
  );
}
