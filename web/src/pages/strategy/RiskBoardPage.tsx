import { useEffect, useState } from 'react';
import { api } from '../../api/http';
import { useApi } from '../../hooks/useApi';
import {
  Badge,
  Card,
  Grid,
  JsonBlock,
  Notice,
  Page,
  Stat,
  statusTone,
} from '../../components/ui';

interface RiskOverview {
  summary?: Record<string, number>;
  strategies?: Array<Record<string, any>>;
  portfolios?: Array<Record<string, any>>;
  alerts?: Array<Record<string, any>>;
}

function pct(value: number | undefined | null): string {
  if (value === undefined || value === null) return '-';
  return `${(value * 100).toFixed(2)}%`;
}

export default function RiskBoardPage() {
  const [auto, setAuto] = useState(true);
  const overview = useApi<RiskOverview>(() => api.get<RiskOverview>('/api/strategy/risk/overview'), []);

  // 10s 自动刷新
  useEffect(() => {
    if (!auto) return;
    const timer = setInterval(() => overview.reload(), 10000);
    return () => clearInterval(timer);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [auto]);

  const summary = overview.data?.summary ?? {};
  const strategies = (overview.data?.strategies ?? []).filter(
    (row: any) => row.circuitEnabled || row.circuitTripped || row.status === 'RUNNING',
  );
  const portfolios = overview.data?.portfolios ?? [];
  const alerts = overview.data?.alerts ?? [];

  return (
    <Page title="策略风控看板" subtitle="运行策略、回撤熔断与组合风控、近期风控告警">
      <Card title="总览">
        <Grid columns={4}>
          <Stat label="运行中策略" value={`${summary.runningStrategies ?? 0} / ${summary.totalStrategies ?? 0}`} />
          <Stat label="已配置熔断" value={String(summary.circuitConfigured ?? 0)} />
          <Stat label="已触发熔断" value={String(summary.circuitTripped ?? 0)} />
          <Stat label="组合风控" value={`${summary.portfolioTripped ?? 0} / ${summary.portfolios ?? 0}`} />
        </Grid>
        <label className="muted" style={{ display: 'inline-flex', alignItems: 'center', gap: 6, marginTop: 10 }}>
          <input type="checkbox" checked={auto} onChange={(event) => setAuto(event.target.checked)} />
          自动刷新（10s）
        </label>
      </Card>

      {overview.error && <Notice tone="danger">看板加载失败：{overview.error}</Notice>}

      <Card title="策略回撤熔断状态">
        <table style={{ width: '100%', borderCollapse: 'collapse' }}>
          <thead>
            <tr>
              <th>策略</th><th>状态</th><th>熔断</th><th>阈值</th><th>峰值浮盈率</th><th>已触发</th><th>触发时间</th>
            </tr>
          </thead>
          <tbody>
            {strategies.map((row: any) => (
              <tr key={row.strategyId}>
                <td>{row.strategyId}</td>
                <td><Badge tone={statusTone(row.status)}>{row.status}</Badge></td>
                <td>{row.circuitEnabled ? '已启用' : '-'}</td>
                <td>{pct(row.maxDrawdownPct)}</td>
                <td>{pct(row.peakPnlRate)}</td>
                <td>
                  <Badge tone={row.circuitTripped ? 'danger' : 'ok'}>
                    {row.circuitTripped ? '已触发' : '正常'}
                  </Badge>
                </td>
                <td>{row.lastTripTime ?? '-'}</td>
              </tr>
            ))}
            {strategies.length === 0 && (
              <tr><td colSpan={7} className="muted">暂无运行中或已配置熔断的策略</td></tr>
            )}
          </tbody>
        </table>
      </Card>

      <Card title="组合风控状态">
        <table style={{ width: '100%', borderCollapse: 'collapse' }}>
          <thead>
            <tr><th>组合</th><th>成员</th><th>阈值</th><th>峰值浮盈率</th><th>状态</th><th>触发时间</th></tr>
          </thead>
          <tbody>
            {portfolios.map((row: any) => (
              <tr key={row.portfolioKey}>
                <td>{row.portfolioKey}</td>
                <td>{(row.members ?? []).join(', ')}</td>
                <td>{pct(row.maxDrawdownPct)}</td>
                <td>{pct(row.peakPnlRate)}</td>
                <td>
                  <Badge tone={row.tripped ? 'danger' : row.enabled ? 'ok' : 'muted'}>
                    {row.tripped ? '已触发' : row.enabled ? '监控中' : '已关闭'}
                  </Badge>
                </td>
                <td>{row.lastTripTime ?? '-'}</td>
              </tr>
            ))}
            {portfolios.length === 0 && (
              <tr><td colSpan={6} className="muted">暂无组合风控配置</td></tr>
            )}
          </tbody>
        </table>
      </Card>

      <Card title="近期风控告警">
        {alerts.length === 0 ? (
          <div className="muted">暂无风控告警</div>
        ) : (
          <table style={{ width: '100%', borderCollapse: 'collapse' }}>
            <thead>
              <tr><th>时间</th><th>规则</th><th>级别</th><th>对象</th><th>状态</th><th>说明</th></tr>
            </thead>
            <tbody>
              {alerts.map((row: any) => (
                <tr key={row.eventId}>
                  <td>{String(row.createTime ?? '').slice(0, 19)}</td>
                  <td>{row.ruleId}</td>
                  <td><Badge tone={row.severity === 'CRITICAL' ? 'danger' : 'warn'}>{row.severity}</Badge></td>
                  <td>{row.metricKey}</td>
                  <td>{row.status}</td>
                  <td style={{ maxWidth: 420 }}>{row.message}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
        <div style={{ marginTop: 10 }}>
          <JsonBlock value={JSON.stringify(summary)} />
        </div>
      </Card>
    </Page>
  );
}