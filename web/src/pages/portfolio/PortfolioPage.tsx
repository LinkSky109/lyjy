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

interface Portfolio {
  id?: number;
  portfolioId: string;
  name?: string;
  owner?: string;
  status?: string;
  baseCurrency?: string;
  configJson?: string;
  createTime?: string;
  updateTime?: string;
}

interface PortfolioPosition {
  id?: number;
  portfolioId?: string;
  symbol: string;
  exchange?: string;
  direction?: string;
  targetQty?: number | string;
  curQty?: number | string;
  weightLimit?: number | string;
}

interface RebalanceInstruction {
  index?: number;
  symbol?: string;
  exchange?: string;
  direction?: string;
  offsetFlag?: string;
  quantity?: number | string;
  price?: number | string;
  estimatedCost?: number | string;
  status?: string;
  reason?: string;
  orderId?: string;
}

interface RebalancePreview {
  instructions?: RebalanceInstruction[];
  diffTotal?: number | string;
  costEst?: number | string;
}

interface RebalanceJob {
  jobId: string;
  portfolioId?: string;
  triggerType?: string;
  status?: string;
  executeMode?: string;
  diffTotal?: number | string;
  costEst?: number | string;
}

interface Deviation {
  symbol?: string;
  direction?: string;
  deviation?: number | string;
}

interface PortfolioDetail {
  portfolio?: Portfolio;
  positions?: PortfolioPosition[];
  preview?: RebalancePreview;
  deviations?: Deviation[];
}

const TRIGGER_TYPES = ['MANUAL', 'SCHEDULED', 'RISK'];
const EXECUTE_MODES = ['MANUAL', 'AUTO'];

export default function PortfolioPage() {
  const [tab, setTab] = useState('portfolio');
  const [busy, setBusy] = useState(false);
  const [actionError, setActionError] = useState<string | null>(null);
  const [okMsg, setOkMsg] = useState<string | null>(null);

  const [selectedId, setSelectedId] = useState('');

  const [name, setName] = useState('');
  const [baseCurrency, setBaseCurrency] = useState('CNY');
  const [configJson, setConfigJson] = useState('{\n  "accountId": "DEFAULT-001"\n}');

  const [positionsJson, setPositionsJson] = useState(
    '[\n  { "symbol": "rb2401", "exchange": "SHFE", "direction": "LONG", "targetQty": 10 }\n]',
  );

  const [triggerType, setTriggerType] = useState('MANUAL');
  const [executeMode, setExecuteMode] = useState('MANUAL');
  const [jobId, setJobId] = useState('');
  const [job, setJob] = useState<RebalanceJob | null>(null);
  const [preview, setPreview] = useState<RebalancePreview | null>(null);
  const [executeConfirmOpen, setExecuteConfirmOpen] = useState(false);

  const list = useApi(() => api.get<Portfolio[]>('/api/portfolio/list'), []);
  const detail = useApi<PortfolioDetail | null>(
    () =>
      selectedId
        ? api.get<PortfolioDetail>(`/api/portfolio/${selectedId}`)
        : Promise.resolve(null),
    [selectedId],
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

  const createPortfolio = () =>
    run(async () => {
      if (!name.trim()) {
        throw new Error('组合名称不能为空');
      }
      const created = await api.post<Portfolio>('/api/portfolio', {
        name: name.trim(),
        baseCurrency,
        configJson,
      });
      setOkMsg(`组合已创建：${created.portfolioId}`);
      setName('');
      list.reload();
    });

  const savePositions = () =>
    run(async () => {
      if (!selectedId) {
        throw new Error('请先选择组合');
      }
      const parsed: unknown = JSON.parse(positionsJson);
      if (!Array.isArray(parsed)) {
        throw new Error('目标持仓需为 JSON 数组');
      }
      await api.post<PortfolioPosition[]>(
        `/api/portfolio/${selectedId}/positions`,
        parsed,
      );
      setOkMsg('目标持仓已保存');
      detail.reload();
    });

  const triggerRebalance = () =>
    run(async () => {
      if (!selectedId) {
        throw new Error('请先选择组合');
      }
      const created = await api.post<RebalanceJob>(
        `/api/portfolio/${selectedId}/rebalance`,
        undefined,
        { params: { triggerType, executeMode } },
      );
      setJob(created);
      setJobId(created.jobId);
      setPreview(null);
      setOkMsg(`已创建再平衡任务 ${created.jobId}（${created.status ?? '-'}）`);
    });

  const loadPreview = () =>
    run(async () => {
      if (!jobId.trim()) {
        throw new Error('请填写任务 ID');
      }
      const result = await api.post<RebalancePreview>(
        `/api/portfolio/rebalance/${jobId.trim()}/preview`,
      );
      setPreview(result);
      setOkMsg('已获取再平衡预览');
    });

  const loadJob = () =>
    run(async () => {
      if (!jobId.trim()) {
        throw new Error('请填写任务 ID');
      }
      const result = await api.post<RebalancePreview>(
        `/api/portfolio/rebalance/${jobId.trim()}/preview`,
      );
      setJob((prev) =>
        prev && prev.jobId === jobId.trim()
          ? prev
          : { jobId: jobId.trim(), status: 'PREVIEW_READY' },
      );
      setPreview(result);
      setOkMsg('已加载任务预览');
    });

  const confirmExecute = () =>
    run(async () => {
      if (!jobId.trim()) {
        throw new Error('请填写任务 ID');
      }
      const { token } = await api.confirmToken();
      const result = await api.post<RebalanceJob>(
        `/api/portfolio/rebalance/${jobId.trim()}/confirm`,
        undefined,
        { token },
      );
      setJob(result);
      setOkMsg(`再平衡执行完成，状态：${result.status ?? '-'}`);
      setExecuteConfirmOpen(false);
      detail.reload();
    });

  const portfolioColumns: Column[] = [
    { key: 'portfolioId', title: '组合 ID' },
    { key: 'name', title: '名称' },
    { key: 'owner', title: '归属人' },
    {
      key: 'status',
      title: '状态',
      render: (row) => <Badge tone={statusTone(row.status)}>{row.status ?? '-'}</Badge>,
    },
    { key: 'baseCurrency', title: '基础币种' },
    {
      key: 'actions',
      title: '操作',
      render: (row) => (
        <Button
          size="sm"
          variant={row.portfolioId === selectedId ? 'primary' : 'default'}
          onClick={() => {
            setSelectedId(row.portfolioId);
            setTab('portfolio');
          }}
        >
          {row.portfolioId === selectedId ? '已选' : '选择'}
        </Button>
      ),
    },
  ];

  const positionColumns: Column[] = [
    { key: 'symbol', title: '标的' },
    { key: 'exchange', title: '交易所' },
    {
      key: 'direction',
      title: '方向',
      render: (row) => <Badge tone={statusTone(row.direction)}>{row.direction ?? '-'}</Badge>,
    },
    { key: 'targetQty', title: '目标数量', align: 'right' },
    { key: 'curQty', title: '当前数量', align: 'right' },
    { key: 'weightLimit', title: '权重上限', align: 'right' },
  ];

  const deviationColumns: Column[] = [
    { key: 'symbol', title: '标的' },
    { key: 'direction', title: '方向' },
    { key: 'deviation', title: '偏差', align: 'right' },
  ];

  const instructionColumns: Column[] = [
    { key: 'index', title: '#', width: '48px' },
    { key: 'symbol', title: '标的' },
    { key: 'exchange', title: '交易所' },
    { key: 'direction', title: '方向' },
    { key: 'offsetFlag', title: '开平' },
    { key: 'quantity', title: '数量', align: 'right' },
    { key: 'price', title: '价格', align: 'right' },
    { key: 'estimatedCost', title: '预估成本', align: 'right' },
    {
      key: 'status',
      title: '状态',
      render: (row) => <Badge tone={statusTone(row.status)}>{row.status ?? '-'}</Badge>,
    },
    { key: 'reason', title: '说明' },
  ];

  const detailData = detail.data;
  const portfolio = detailData?.portfolio;
  const positions = detailData?.positions ?? [];
  const deviations = detailData?.deviations ?? [];
  const instructions = preview?.instructions ?? [];

  return (
    <Page title="组合管理与再平衡" subtitle="组合定义、目标持仓、再平衡预览与二次确认执行">
      <Tabs
        tabs={[
          { key: 'portfolio', label: '组合' },
          { key: 'rebalance', label: '再平衡' },
        ]}
        active={tab}
        onChange={setTab}
      />

      {actionError && <Notice tone="danger">{actionError}</Notice>}
      {okMsg && <Notice tone="ok">{okMsg}</Notice>}

      {tab === 'portfolio' && (
        <>
          <Card title="新建组合">
            <Field label="组合名称" hint="必填，后端自动生成 portfolioId（PF-xxxxxxxx）">
              <Input value={name} onChange={(event) => setName(event.target.value)} placeholder="示例：多品种套利组合" />
            </Field>
            <Field label="基础币种">
              <Input value={baseCurrency} onChange={(event) => setBaseCurrency(event.target.value)} placeholder="CNY" />
            </Field>
            <Field label="配置 JSON" hint="建议包含 accountId，再平衡确认时用于报单账户">
              <Textarea value={configJson} onChange={(event) => setConfigJson(event.target.value)} />
            </Field>
            <Button variant="primary" disabled={busy} onClick={createPortfolio}>
              创建组合
            </Button>
          </Card>

          <Card title="组合列表">
            <AsyncState
              loading={list.loading}
              error={list.error}
              onRetry={list.reload}
              empty={!list.data || list.data.length === 0 ? '暂无组合' : false}
            >
              <DataTable
                columns={portfolioColumns}
                rows={list.data}
                rowKey={(row) => row.portfolioId}
              />
            </AsyncState>
          </Card>

          <Card title="组合详情" actions={<Button size="sm" disabled={busy} onClick={() => detail.reload()}>刷新</Button>}>
            {!selectedId ? (
              <div className="empty">请先在上方列表选择组合</div>
            ) : (
              <AsyncState loading={detail.loading} error={detail.error} onRetry={detail.reload}>
                {portfolio && (
                  <KeyValue
                    items={[
                      { label: '组合 ID', value: portfolio.portfolioId },
                      { label: '名称', value: portfolio.name ?? '-' },
                      { label: '归属人', value: portfolio.owner ?? '-' },
                      {
                        label: '状态',
                        value: <Badge tone={statusTone(portfolio.status)}>{portfolio.status ?? '-'}</Badge>,
                      },
                      { label: '基础币种', value: portfolio.baseCurrency ?? '-' },
                      { label: '更新时间', value: portfolio.updateTime ?? '-' },
                    ]}
                  />
                )}

                <h3 className="subsection-title">当前持仓</h3>
                <DataTable columns={positionColumns} rows={positions} empty="暂无持仓" rowKey={(row) => `${row.symbol}-${row.direction}`} />

                <h3 className="subsection-title">目标偏差</h3>
                <DataTable columns={deviationColumns} rows={deviations} empty="暂无偏差" rowKey={(row, index) => `${row.symbol}-${row.direction}-${index}`} />

                <h3 className="subsection-title">再平衡预览</h3>
                <DataTable columns={instructionColumns} rows={detailData?.preview?.instructions ?? []} empty="暂无预览" rowKey={(row, index) => `${row.symbol}-${index}`} />
              </AsyncState>
            )}
          </Card>

          <Card title="持仓分布" actions={<span className="muted">按当前数量占比</span>}>
            {positions.length === 0 ? (
              <div className="empty">
                暂无持仓分布。请先在「组合列表」选择组合并设置目标持仓。
              </div>
            ) : (
              <div className="bar-list">
                {(() => {
                  const weighted = positions.map((row) => ({
                    label: `${row.symbol} · ${row.direction ?? '-'}`,
                    value: Math.abs(Number(row.curQty ?? row.targetQty ?? 0)),
                  }));
                  const total = weighted.reduce((sum, item) => sum + item.value, 0);
                  return weighted
                    .sort((a, b) => b.value - a.value)
                    .slice(0, 8)
                    .map((item) => {
                      const ratio = total > 0 ? (item.value / total) * 100 : 0;
                      return (
                        <div className="bar-item" key={item.label}>
                          <div className="bar-item-head">
                            <span>{item.label}</span>
                            <span className="num">
                              {item.value.toLocaleString('zh-CN')} · {ratio.toFixed(1)}%
                            </span>
                          </div>
                          <div className="bar-track">
                            <div className="bar-fill" style={{ width: `${ratio}%` }} />
                          </div>
                        </div>
                      );
                    });
                })()}
              </div>
            )}
            <div className="mt-3">
              <Notice tone="info">
                后端未提供组合净值历史与策略贡献接口，本轮不绘制净值曲线；分布按持仓当前数量计算。
              </Notice>
            </div>
          </Card>

          <Card title="设置目标持仓">
            <Notice tone="info">提交后为全量覆盖：body 为 PortfolioPosition 数组，symbol 必填，targetQty 缺省按 0 处理。</Notice>
            <Field label="目标持仓 JSON（PortfolioPosition 数组）" hint="symbol 必填，targetQty 缺省按 0 处理">
              <Textarea value={positionsJson} onChange={(event) => setPositionsJson(event.target.value)} />
            </Field>
            <div className="mt-3">
              <Button disabled={busy || !selectedId} onClick={savePositions}>
                保存目标持仓
              </Button>
            </div>
          </Card>
        </>
      )}

      {tab === 'rebalance' && (
        <>
          <Card title="触发再平衡">
            {!selectedId && <Notice tone="warn">请先在「组合」页签选择一个组合。</Notice>}
            <Toolbar>
              <Field label="触发类型">
                <Select value={triggerType} onChange={(event) => setTriggerType(event.target.value)}>
                  {TRIGGER_TYPES.map((item) => (
                    <option key={item} value={item}>
                      {item}
                    </option>
                  ))}
                </Select>
              </Field>
              <Field label="执行模式">
                <Select value={executeMode} onChange={(event) => setExecuteMode(event.target.value)}>
                  {EXECUTE_MODES.map((item) => (
                    <option key={item} value={item}>
                      {item}
                    </option>
                  ))}
                </Select>
              </Field>
              <Button variant="primary" disabled={busy || !selectedId} onClick={triggerRebalance}>
                触发再平衡
              </Button>
            </Toolbar>
          </Card>

          <Card title="任务预览与确认">
            <Toolbar>
              <Field label="任务 ID" hint="触发后自动回填，也可手动输入已有任务">
                <Input value={jobId} onChange={(event) => setJobId(event.target.value)} placeholder="RB-XXXXXXXX" />
              </Field>
              <Button disabled={busy} onClick={loadPreview}>
                拉取预览
              </Button>
              <Button disabled={busy} onClick={loadJob}>
                加载任务
              </Button>
            </Toolbar>

            {job && (
              <div className="mt-3">
                <KeyValue
                  items={[
                    { label: '任务 ID', value: job.jobId },
                    { label: '组合 ID', value: job.portfolioId ?? '-' },
                    {
                      label: '状态',
                      value: <Badge tone={statusTone(job.status)}>{job.status ?? '-'}</Badge>,
                    },
                    { label: '触发类型', value: job.triggerType ?? '-' },
                    { label: '执行模式', value: job.executeMode ?? '-' },
                    { label: '总偏差', value: job.diffTotal ?? '-' },
                    { label: '预估成本', value: job.costEst ?? '-' },
                  ]}
                />
              </div>
            )}

            {(preview || job) && (
              <div className="mt-3">
                <DataTable
                  columns={instructionColumns}
                  rows={instructions}
                  empty="预览无指令"
                  rowKey={(row, index) => `${row.symbol}-${index}`}
                />
              </div>
            )}

            <Notice tone="warn">
              确认执行是敏感操作：将签发一次性确认令牌（30s）后提交真实报单，不可撤销。
            </Notice>
            <div className="mt-3">
              <Button
                variant="danger"
                disabled={busy || !jobId.trim()}
                onClick={() => setExecuteConfirmOpen(true)}
              >
                确认执行再平衡
              </Button>
            </div>
          </Card>

          <Card title="原始预览 JSON">
            <JsonBlock value={preview ?? detailData?.preview ?? {}} />
          </Card>
        </>
      )}
      <Modal
        open={executeConfirmOpen}
        title="确认执行再平衡"
        onClose={() => setExecuteConfirmOpen(false)}
        footer={
          <>
            <Button onClick={() => setExecuteConfirmOpen(false)} disabled={busy}>
              取消
            </Button>
            <Button variant="danger" onClick={confirmExecute} disabled={busy}>
              {busy ? '执行中…' : '确认执行'}
            </Button>
          </>
        }
      >
        <Notice tone="danger">
          该操作会签发一次性确认令牌并提交真实报单，不可撤销。请核对任务与指令。
        </Notice>
        <KeyValue
          items={[
            { label: '任务 ID', value: jobId.trim() || '-' },
            { label: '组合', value: selectedId || '-' },
            { label: '指令数', value: String(instructions.length) },
            { label: '预估成本', value: String(preview?.costEst ?? job?.costEst ?? '-') },
          ]}
        />
      </Modal>
    </Page>
  );
}
