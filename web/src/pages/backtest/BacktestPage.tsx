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
        ? await api.post<BacktestResult>('/api/backtest/run-real?frequency=1h', payload)
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
        </Toolbar>
        {runError && <Notice tone="danger">{runError}</Notice>}
        <Notice tone="warn">
          真实行情回测使用 ClickHouse 中的真实 K 线（默认 1h），数据来自 Gate.io；
          如无数据请先执行 POST /api/market/real/refresh。
        </Notice>
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