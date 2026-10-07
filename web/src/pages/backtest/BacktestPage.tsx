import { useState } from 'react';
import { api, errorText } from '../../api/http';
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
  Stat,
  Toolbar,
  statusTone,
  type Column,
} from '../../components/ui';
interface BacktestMetrics {
  totalReturn?: number;
  annualizedReturn?: number;
  maxDrawdown?: number;
  sharpeRatio?: number;
  sortinoRatio?: number;
  winRate?: number;
  profitLossRatio?: number;
  totalTrades?: number;
  winningTrades?: number;
  losingTrades?: number;
  annualizedVolatility?: number;
  backtestDays?: number;
}

interface BacktestTrade {
  tradeTime?: string;
  symbol?: string;
  direction?: string;
  price?: number;
  quantity?: number;
  totalCost?: number;
  commission?: number;
  realizedPnl?: number;
}

interface BacktestResult {
  backtestId?: string;
  strategyName?: string;
  status?: string;
  startTime?: string;
  endTime?: string;
  durationMs?: number;
  metrics?: BacktestMetrics;
  trades?: BacktestTrade[];
  lookaheadWarnings?: string[];
  errorMessage?: string;
}

function pad(value: number): string {
  return String(value).padStart(2, '0');
}

function localDateTime(date: Date): string {
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

function toIso(value: string): string | undefined {
  const text = value.trim();
  if (text === '') return undefined;
  return text.length === 16 ? `${text}:00` : text;
}

function toNum(value: string): number | undefined {
  const text = value.trim();
  if (text === '') return undefined;
  const parsed = Number(text);
  return Number.isFinite(parsed) ? parsed : undefined;
}

function pct(value: number | undefined): string {
  if (value === undefined || value === null) return '-';
  return `${(value * 100).toFixed(2)}%`;
}

type TaskState = 'idle' | 'running' | 'done';

export default function BacktestPage() {
  const [strategyName, setStrategyName] = useState('BUILTIN_SMA');
  const [symbols, setSymbols] = useState('BTCUSDT');
  const defaultStart = new Date(Date.now() - 30 * 86400_000);
  const [start, setStart] = useState(() => localDateTime(defaultStart));
  const [end, setEnd] = useState(() => localDateTime(new Date()));
  const [capital, setCapital] = useState('1000000');
  const [commissionPct, setCommissionPct] = useState('0.03');
  const [slippage, setSlippage] = useState('0.01');

  const [task, setTask] = useState<TaskState>("idle");
  const [result, setResult] = useState<BacktestResult | null>(null);
  const [runError, setRunError] = useState<string | null>(null);
  const [signalClose, setSignalClose] = useState(false);

  // 参数优化（OBS-011）
  const [optFast, setOptFast] = useState('3,5,8');
  const [optSlow, setOptSlow] = useState('15,20,30');
  const [optObjective, setOptObjective] = useState('SHARPE');
  const [optResult, setOptResult] = useState<any[] | null>(null);
  const [optBusy, setOptBusy] = useState(false);
  const [optError, setOptError] = useState<string | null>(null);
  const [optStrategyId, setOptStrategyId] = useState('');
  const [applyInfo, setApplyInfo] = useState<any>(null);

  const runOptimize = async () => {
    setOptError(null);
    setOptResult(null);
    setApplyInfo(null);
    setOptBusy(true);
    try {
      const fasts = optFast.split(',').map((x) => Number(x.trim())).filter((n) => n > 0);
      const slows = optSlow.split(',').map((x) => Number(x.trim())).filter((n) => n > 0);
      const tickSymbols = symbols.split(',').map((s) => s.trim()).filter(Boolean);
      if (fasts.length === 0 || slows.length === 0) {
        throw new Error('请填写快慢周期候选（逗号分隔）');
      }
      const result = await api.post<any[]>('/api/backtest/optimize', {
        symbols: tickSymbols.length > 0 ? tickSymbols : ['BTC_USDT'],
        frequency: '1h',
        mode: signalClose ? 'close' : 'ohlc',
        startTime: toIso(start),
        endTime: toIso(end),
        fastOptions: fasts,
        slowOptions: slows,
        objective: optObjective,
        maxDrawdownLimit: 0.2,
        quantity: 1,
      });
      setOptResult(result);
    } catch (error) {
      setOptError(`优化失败：${errorText(error)}`);
    } finally {
      setOptBusy(false);
    }
  };

  const applyBest = async () => {
    const first = optResult?.[0];
    if (!first?.batchId) {
      return;
    }
    setApplyInfo(null);
    setOptBusy(true);
    try {
      const params = new URLSearchParams({ batchId: first.batchId });
      if (optStrategyId.trim()) {
        params.set('strategyId', optStrategyId.trim());
      }
      const result = await api.post<any>(`/api/backtest/optimize/apply?${params.toString()}`);
      setApplyInfo(result);
    } catch (error) {
      setOptError(`应用失败：${errorText(error)}`);
    } finally {
      setOptBusy(false);
    }
  };

  const runBacktest = async (useRealData: boolean) => {
    setRunError(null);
    setResult(null);
    const tickSymbols = symbols
      .split(',')
      .map((item) => item.trim())
      .filter(Boolean);
    if (tickSymbols.length === 0) {
      setRunError('请填写标的 symbols（逗号分隔）');
      return;
    }
    const commission = toNum(commissionPct);
    if (commission === undefined) {
      setRunError('手续费率必须为数字（%）');
      return;
    }
    setTask("running");
    try {
      const payload = {
        strategyName: strategyName.trim() || 'BUILTIN_SMA',
        symbols: tickSymbols,
        startTime: toIso(start),
        endTime: toIso(end),
        initialCapital: toNum(capital) ?? 1000000,
        commissionRate: commission / 100,
        slippage: toNum(slippage) ?? 0.01,
      };
      // 真实行情回测：后端从 ClickHouse 读真实 K 线（需先 /api/market/real/refresh），
      // 数据频率默认 1h（Gate.io 已入库存档）。
      const resultData = useRealData
        ? await api.post<BacktestResult>(
            `/api/backtest/run-real?frequency=1h&mode=${signalClose ? 'close' : 'ohlc'}`,
            payload,
          )
        : await api.post<BacktestResult>('/api/backtest/run', payload);
      setResult(resultData);
      setTask("done");
    } catch (error) {
      setRunError(`回测失败：${errorText(error)}`);
      setTask("done");
    }
  };

  const metrics: BacktestMetrics | undefined = result?.metrics;
  const trades = result?.trades ?? [];
  const warnings = result?.lookaheadWarnings ?? [];

  const tradeColumns: Column[] = [
    { key: 'tradeTime', title: '时间' },
    { key: 'symbol', title: '标的' },
    {
      key: 'direction',
      title: '方向',
      render: (row) => <Badge tone={statusTone(row.direction)}>{row.direction ?? '-'}</Badge>,
    },
    { key: 'price', title: '价格' },
    { key: 'quantity', title: '数量' },
    { key: 'commission', title: '手续费' },
    { key: 'totalCost', title: '成本' },
    { key: 'realizedPnl', title: '已实现盈亏' },
  ];

  return (
    <Page title="回测" subtitle="tick 级回测：配置参数并提交，异步运行后查看绩效与交易明细">
      <Card title="回测配置">
        <Grid columns={2}>
          <Field label="策略（strategyName）" hint="当前使用内置双均线 BUILTIN_SMA">
            <Input value={strategyName} onChange={(event) => setStrategyName(event.target.value)} />
          </Field>
          <Field label="标的（symbols）" hint="逗号分隔，如 BTCUSDT,ETHUSDT">
            <Input value={symbols} onChange={(event) => setSymbols(event.target.value)} />
          </Field>
          <Field label="开始时间">
            <Input type="datetime-local" value={start} onChange={(event) => setStart(event.target.value)} />
          </Field>
          <Field label="结束时间">
            <Input type="datetime-local" value={end} onChange={(event) => setEnd(event.target.value)} />
          </Field>
          <Field label="初始资金">
            <Input value={capital} onChange={(event) => setCapital(event.target.value)} />
          </Field>
          <Field label="手续费率（%）">
            <Input value={commissionPct} onChange={(event) => setCommissionPct(event.target.value)} />
          </Field>
          <Field label="滑点">
            <Input value={slippage} onChange={(event) => setSlippage(event.target.value)} />
          </Field>
        </Grid>
        <Toolbar>
          <Button variant="primary" disabled={task === "running"} onClick={() => runBacktest(false)}>
            {task === "running" ? '回测运行中…' : '运行回测（模拟）'}
          </Button>
          <Button variant="default" disabled={task === "running"} onClick={() => runBacktest(true)}>
            真实行情回测
          </Button>
          <label className="muted" style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}>
            <input
              type="checkbox"
              checked={signalClose}
              onChange={(event) => setSignalClose(event.target.checked)}
            />
            收盘信号（close-only，真实回测）
          </label>
        </Toolbar>
        {runError && <Notice tone="danger">{runError}</Notice>}
        <Notice tone="warn">
          真实行情回测使用 ClickHouse 中的真实 K 线（默认 1h），数据来自 Gate.io；
          如无数据请先执行 POST /api/market/real/refresh。
        </Notice>
      </Card>

      <Card title="策略参数优化（双均线网格寻优，真实行情）">
        <Grid columns={3}>
          <Field label="快线周期候选（逗号分隔）">
            <Input value={optFast} onChange={(event) => setOptFast(event.target.value)} />
          </Field>
          <Field label="慢线周期候选（逗号分隔）">
            <Input value={optSlow} onChange={(event) => setOptSlow(event.target.value)} />
          </Field>
          <Field label="目标函数">
            <select
              value={optObjective}
              onChange={(event) => setOptObjective(event.target.value)}
              style={{ width: '100%', padding: 8 }}
            >
              <option value="SHARPE">夏普比率</option>
              <option value="TOTAL_RETURN">总收益</option>
              <option value="CALMAR">卡玛比率</option>
            </select>
          </Field>
        </Grid>
        <Toolbar>
          <Button variant="primary" disabled={optBusy} onClick={runOptimize}>
            {optBusy ? '优化中…' : '参数优化'}
          </Button>
        </Toolbar>
        {optError && <Notice tone="danger">{optError}</Notice>}
        {optResult && optResult.length > 0 && (
          <div style={{ marginTop: 16 }}>
            <Toolbar>
              <span className="muted">
                批次 {optResult[0].batchId}（{optResult.length} 标的）
              </span>
              <Field label="策略 ID（可选，落档最优参数到 params_json）">
                <Input
                  value={optStrategyId}
                  onChange={(event) => setOptStrategyId(event.target.value)}
                  placeholder="SIG-TRADE"
                />
              </Field>
              <Button variant="default" disabled={optBusy} onClick={applyBest}>
                应用最优（复验）
              </Button>
            </Toolbar>
            {optResult.map((res: any, idx: number) => (
              <div key={idx} style={{ marginTop: 12 }}>
                <Toolbar>
                  <Badge tone="ok">{res.symbol} 最优</Badge>
                  <span className="muted">
                    快线 {res.best?.fast} / 慢线 {res.best?.slow} · ret {pct(res.best?.totalReturn)} ·
                    dd {pct(res.best?.maxDrawdown)} · 交易 {res.best?.trades} 笔（基准 5/20：ret{' '}
                    {pct(res.baseline?.totalReturn)}）
                  </span>
                </Toolbar>
                <table style={{ width: '100%', borderCollapse: 'collapse', marginTop: 8 }}>
                  <thead>
                    <tr>
                      <th>快线</th><th>慢线</th><th>总收益</th><th>夏普</th><th>最大回撤</th><th>交易数</th>
                    </tr>
                  </thead>
                  <tbody>
                    {(res.runs ?? []).map((run: any, index: number) => (
                      <tr key={index} style={run === res.best ? { background: '#e6f4ea' } : undefined}>
                        <td>{run.fast}</td><td>{run.slow}</td>
                        <td>{pct(run.totalReturn)}</td><td>{run.sharpe?.toFixed(3)}</td>
                        <td>{pct(run.maxDrawdown)}</td><td>{run.trades}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            ))}
            {applyInfo && (
              <Notice tone="ok">
                应用结果：参数落档={String(applyInfo.applied)}
                {applyInfo.strategyId ? `（策略 ${applyInfo.strategyId}）` : ''} · 复验 ret=
                {pct(applyInfo.verify?.metrics?.totalReturn)} · 交易=
                {applyInfo.verify?.metrics?.totalTrades} · dd=
                {pct(applyInfo.verify?.metrics?.maxDrawdown)}
              </Notice>
            )}
          </div>
        )}
      </Card>

      {task !== "idle" && (
        <Card title="回测结果">
          <AsyncState
            loading={task === "running"}
            error={null}
            empty={!result ? '等待回测结果…' : false}
          >
            {result && (
              <>
                <Toolbar>
                  <Badge tone={statusTone(result.status)}>{result.status ?? '-'}</Badge>
                  <span className="muted">
                    {result.backtestId} · 策略 {result.strategyName} · 耗时{' '}
                    {result.durationMs != null ? `${(result.durationMs / 1000).toFixed(1)}s` : '-'}
                  </span>
                </Toolbar>

                {warnings.length > 0 && (
                  <Notice tone="warn">未来函数检测：{warnings.join('；')}</Notice>
                )}
                {result.errorMessage && <Notice tone="danger">{result.errorMessage}</Notice>}

                <div style={{ marginTop: 16 }}>
                  <Grid columns={4}>
                    <Stat label="总收益" value={pct(metrics?.totalReturn)} />
                    <Stat label="年化收益" value={pct(metrics?.annualizedReturn)} />
                    <Stat label="夏普比率" value={metrics?.sharpeRatio?.toFixed(2) ?? '-'} />
                    <Stat label="最大回撤" value={pct(metrics?.maxDrawdown)} />
                    <Stat label="胜率" value={pct(metrics?.winRate)} />
                    <Stat label="盈亏比" value={metrics?.profitLossRatio?.toFixed(2) ?? '-'} />
                    <Stat label="交易次数" value={String(metrics?.totalTrades ?? '-')} />
                    <Stat label="回测天数" value={String(metrics?.backtestDays ?? '-')} />
                  </Grid>
                </div>

                <h3 style={{ margin: '20px 0 8px', fontSize: 14 }}>交易明细（{trades.length} 笔）</h3>
                <DataTable
                  columns={tradeColumns}
                  rows={trades}
                  rowKey={(row, index) => String((row as BacktestTrade)?.tradeTime ?? index)}
                />
              </>
            )}
          </AsyncState>
        </Card>
      )}
    </Page>
  );
}