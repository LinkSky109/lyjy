import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api } from '../../api/http';
import { useApi } from '../../hooks/useApi';
import {
  AsyncState,
  Badge,
  Button,
  Card,
  DataTable,
  Grid,
  Page,
  Stat,
  statusTone,
} from '../../components/ui';

interface DashboardSummary {
  service: { status: string };
  market: { running: boolean; subscribed: number };
  components: Record<string, string>;
  equity: {
    total: number;
    available: number;
    frozen: number;
    margin: number;
    marginRatio: number;
    accounts: number;
  };
  pnl: {
    unrealized: number;
    realized: number;
    combined: number;
    basis: string;
    todayRealized?: number;
    todayCommission?: number;
    todayCombined?: number;
    todayTrades?: number;
  };
  alerts: { open: number; critical: number };
  orders: { active: number; filledToday: number };
  todos?: {
    pendingCash: number;
    openAlerts: number;
    criticalAlerts: number;
    cancelableOrders: number;
  };
  asOf?: string;
}

interface PositionView {
  id?: number;
  accountId: string;
  strategyId?: string;
  symbol: string;
  direction: string;
  quantity?: number;
  avgCost?: number;
  unrealizedPnl?: number;
}

interface OrderView {
  orderId: string;
  symbol?: string;
  direction?: string;
  offsetFlag?: string;
  price?: number;
  quantity?: number;
  filledQuantity?: number;
  status?: string;
  createTime?: string;
}

interface TimelineItem {
  type: string;
  id: string;
  title: string;
  status?: string;
  severity?: string;
  amount?: number;
  at?: string;
}

interface AlertEvent {
  eventId: string;
  metricKey?: string;
  value?: string;
  severity?: string;
  message?: string;
  status?: string;
  createTime?: string;
}

function money(value: number | undefined | null, digits = 2): string {
  if (value === undefined || value === null) {
    return '-';
  }
  return `¥${value.toLocaleString('zh-CN', {
    minimumFractionDigits: digits,
    maximumFractionDigits: digits,
  })}`;
}

function qty(value: number | undefined | null): string {
  if (value === undefined || value === null) {
    return '-';
  }
  return value.toLocaleString('zh-CN', { maximumFractionDigits: 0 });
}

function pct(value: number | undefined | null): string {
  if (value === undefined || value === null || !Number.isFinite(value)) {
    return '-';
  }
  return `${value.toFixed(1)}%`;
}

function dotClass(ok: boolean | undefined): string {
  if (ok === undefined) {
    return 'dot';
  }
  return ok ? 'dot dot-ok' : 'dot dot-danger';
}

function statusOk(status: string | undefined): boolean | undefined {
  if (!status) {
    return undefined;
  }
  return status === 'UP';
}

export default function DashboardPage() {
  const navigate = useNavigate();
  const [refreshedAt, setRefreshedAt] = useState(() => new Date());

  const summary = useApi(() => api.get<DashboardSummary>('/api/dashboard/summary'), []);
  const alerts = useApi(() => api.get<AlertEvent[]>('/api/monitor/alerts', { status: 'OPEN' }), []);
  const positions = useApi(
    () => api.get<PositionView[]>('/api/trade/platform/positions', { accountId: 'DEFAULT-001' }),
    [],
  );
  const orders = useApi(() => api.get<OrderView[]>('/api/oms/orders/active'), []);
  const timeline = useApi(
    () => api.get<{ items: TimelineItem[] }>('/api/dashboard/timeline', { limit: 12 }),
    [],
  );

  const data = summary.data;
  const components = data?.components ?? {};
  const serviceStatus = data?.service.status;
  const marketRunning = data?.market.running;
  const openAlerts = alerts.data ?? [];
  const positionRows = positions.data ?? [];
  const orderRows = orders.data ?? [];

  const equity = data?.equity.total;
  const pnl = data?.pnl.combined;
  const todayPnl = data?.pnl.todayCombined;
  const todos = data?.todos;
  const timelineRows = timeline.data?.items ?? [];
  const available = data?.equity.available;
  const margin = data?.equity.margin;
  const marginRatio = data?.equity.marginRatio;
  const frozen = data?.equity.frozen;

  const reloadAll = () => {
    summary.reload();
    alerts.reload();
    positions.reload();
    orders.reload();
    timeline.reload();
    setRefreshedAt(new Date());
  };

  return (
    <Page
      title="仪表盘"
      subtitle={`系统健康、资金与未闭环告警总览 · 最近刷新 ${refreshedAt.toLocaleTimeString(
        'zh-CN',
        { hour12: false },
      )}`}
      actions={
        <>
          <Button onClick={reloadAll}>刷新</Button>
          <Button variant="primary" onClick={() => navigate('/trading')}>
            新建委托
          </Button>
        </>
      }
    >
      {summary.error && (
        <div className="error-box" role="alert">
          <div className="error-copy">
            <strong>仪表盘汇总加载失败</strong>
            <span className="error-detail">{summary.error}</span>
            <span className="error-hint">请确认 `/api/dashboard/summary` 可用，然后重试。</span>
          </div>
          <Button size="sm" onClick={summary.reload}>
            重试
          </Button>
        </div>
      )}

      <div className="status-strip">
        <div className="status-item">
          <span className="status-label">核心服务</span>
          <span className="status-value">
            <span className={dotClass(statusOk(serviceStatus))} />
            {serviceStatus ? (serviceStatus === 'UP' ? '健康' : serviceStatus) : '未上报'}
          </span>
        </div>
        <div className="status-item">
          <span className="status-label">行情服务</span>
          <span className="status-value">
            <span className={dotClass(marketRunning)} />
            {data?.market ? (marketRunning ? '运行中' : '已停止') : '未上报'}
            <span className="num">
              {data?.market ? `${data.market.subscribed} 个订阅` : ''}
            </span>
          </span>
        </div>
        <div className="status-item">
          <span className="status-label">PostgreSQL</span>
          <span className="status-value">
            <span className={dotClass(statusOk(components.postgres))} />
            {components.postgres ?? '未上报'}
          </span>
        </div>
        <div className="status-item">
          <span className="status-label">Kafka</span>
          <span className="status-value">
            <span className={dotClass(statusOk(components.kafka))} />
            {components.kafka ?? '未上报'}
          </span>
        </div>
      </div>

      <Card
        title="待办中心"
        actions={
          <span className="muted">
            {todos ? `${todos.pendingCash + todos.openAlerts + todos.cancelableOrders} 项待处理` : '汇总中'}
          </span>
        }
      >
        <div className="todo-grid">
          <button type="button" className="todo-item" onClick={() => navigate('/fund')}>
            <span className="todo-label">待审出入金</span>
            <strong className={todos?.pendingCash ? 'todo-count-warn' : ''}>
              {todos?.pendingCash ?? '-'}
            </strong>
          </button>
          <button type="button" className="todo-item" onClick={() => navigate('/monitor')}>
            <span className="todo-label">未确认告警</span>
            <strong className={todos?.criticalAlerts ? 'todo-count-danger' : ''}>
              {todos?.openAlerts ?? '-'}
            </strong>
          </button>
          <button type="button" className="todo-item" onClick={() => navigate('/monitor')}>
            <span className="todo-label">严重告警</span>
            <strong className={todos?.criticalAlerts ? 'todo-count-danger' : ''}>
              {todos?.criticalAlerts ?? '-'}
            </strong>
          </button>
          <button type="button" className="todo-item" onClick={() => navigate('/trading')}>
            <span className="todo-label">可撤委托</span>
            <strong>{todos?.cancelableOrders ?? '-'}</strong>
          </button>
        </div>
      </Card>

      <Grid columns={4}>
        <Stat label="今日综合盈亏" value={money(todayPnl)} tone={todayPnl === undefined ? 'muted' : todayPnl >= 0 ? 'ok' : 'danger'} hint="已实现盈亏 − 手续费" />
        <Stat label="今日已实现" value={money(data?.pnl.todayRealized)} tone={(data?.pnl.todayRealized ?? 0) >= 0 ? 'ok' : 'danger'} hint="当日成交记录聚合" />
        <Stat label="今日手续费" value={money(data?.pnl.todayCommission)} tone="warn" hint="当日成交手续费" />
        <Stat label="今日成交" value={`${data?.pnl.todayTrades ?? '-'} 笔`} tone="info" hint="当日成交记录数" />
      </Grid>

      <Grid columns={4}>
        <Stat
          label="总权益"
          value={money(equity)}
          tone="info"
          hint={data?.equity ? `${data.equity.accounts} 个资金账户 · 模拟盘` : '后端未返回资金聚合'}
        />
        <Stat
          label="浮动 + 已实现盈亏"
          value={money(pnl)}
          tone={pnl === undefined ? 'muted' : pnl >= 0 ? 'ok' : 'danger'}
          hint={data?.pnl ? '累计口径（后端 basis=CUMULATIVE）' : '后端未返回盈亏数据'}
        />
        <Stat
          label="可用资金"
          value={money(available)}
          tone="ok"
          hint={
            available !== undefined && equity
              ? `占总权益 ${pct((available / equity) * 100)}`
              : '后端未返回资金数据'
          }
        />
        <Stat
          label="保证金占用"
          value={pct(marginRatio)}
          tone={marginRatio !== undefined && marginRatio >= 70 ? 'danger' : 'warn'}
          hint={margin !== undefined ? `保证金 ${money(margin)} · 预警线 70%` : '后端未返回资金数据'}
        />
      </Grid>

      <div className="split">
        <div className="col">
          <Card
            title="待处理告警"
            padded={false}
            actions={
              <>
                <span className="muted">{openAlerts.length} 条未确认</span>
                <Button size="sm" variant="ghost" onClick={() => navigate('/monitor')}>
                  全部告警
                </Button>
              </>
            }
          >
            <AsyncState
              loading={alerts.loading}
              error={alerts.error}
              onRetry={alerts.reload}
              empty={
                !alerts.loading &&
                openAlerts.length === 0 &&
                '暂无未确认告警：阈值正常，或告警规则尚未配置。可到「监控告警」补充规则。'
              }
            >
              <DataTable
                columns={[
                  { key: 'createTime', title: '时间' },
                  {
                    key: 'severity',
                    title: '级别',
                    render: (row: AlertEvent) => (
                      <Badge tone={statusTone(row.severity)}>{row.severity ?? '-'}</Badge>
                    ),
                  },
                  { key: 'metricKey', title: '指标' },
                  { key: 'message', title: '说明' },
                ]}
                rows={openAlerts.slice(0, 4)}
                rowKey={(row: AlertEvent) => row.eventId}
                empty="暂无未确认告警"
              />
            </AsyncState>
          </Card>

          <Card
            title="持仓"
            padded={false}
            actions={
              <>
                <span className="muted">按浮动盈亏排序</span>
                <Button size="sm" variant="ghost" onClick={() => navigate('/portfolio')}>
                  组合视图
                </Button>
              </>
            }
          >
            <AsyncState
              loading={positions.loading}
              error={positions.error}
              onRetry={positions.reload}
              empty={
                !positions.loading &&
                positionRows.length === 0 &&
                '当前账户没有持仓。可到「交易下单」提交第一笔委托。'
              }
            >
              <DataTable
                columns={[
                  { key: 'symbol', title: '合约' },
                  {
                    key: 'direction',
                    title: '方向',
                    render: (row: PositionView) => (
                      <Badge tone={row.direction === 'BUY' || row.direction === 'LONG' ? 'ok' : 'danger'}>
                        {row.direction}
                      </Badge>
                    ),
                  },
                  { key: 'quantity', title: '数量', align: 'right', render: (row: PositionView) => qty(row.quantity) },
                  { key: 'avgCost', title: '均价', align: 'right', render: (row: PositionView) => money(row.avgCost) },
                  {
                    key: 'unrealizedPnl',
                    title: '浮动盈亏',
                    align: 'right',
                    render: (row: PositionView) => (
                      <span className={(row.unrealizedPnl ?? 0) >= 0 ? 'delta-up' : 'delta-down'}>
                        {money(row.unrealizedPnl)}
                      </span>
                    ),
                  },
                ]}
                rows={[...positionRows].sort(
                  (a, b) => (b.unrealizedPnl ?? 0) - (a.unrealizedPnl ?? 0),
                )}
                rowKey={(row: PositionView) =>
                  `${row.accountId}-${row.symbol}-${row.direction}-${row.id ?? ''}`
                }
                empty="暂无持仓"
              />
            </AsyncState>
          </Card>
        </div>

        <div className="col">
          <Card title="账户与风险" actions={<span className="muted">实时</span>}>
            <AsyncState loading={summary.loading} error={summary.error} onRetry={summary.reload}>
              <dl className="kv kv-single">
                <div className="kv-row">
                  <dt>总权益</dt>
                  <dd>{money(equity)}</dd>
                </div>
                <div className="kv-row">
                  <dt>可用 / 冻结</dt>
                  <dd>
                    {money(available)} / {money(frozen)}
                  </dd>
                </div>
                <div className="kv-row">
                  <dt>浮动 / 已实现</dt>
                  <dd>
                    {money(data?.pnl.unrealized)} / {money(data?.pnl.realized)}
                  </dd>
                </div>
              </dl>
              <div className="meter mt-4">
                <div className="meter-head">
                  <span>保证金占用</span>
                  <strong>{pct(marginRatio)}</strong>
                </div>
                <div className="meter-track">
                  <div
                    className={`meter-fill ${
                      marginRatio !== undefined && marginRatio >= 70
                        ? 'danger'
                        : marginRatio !== undefined && marginRatio >= 60
                          ? 'warn'
                          : ''
                    }`}
                    style={{ width: `${Math.min(100, marginRatio ?? 0)}%` }}
                  />
                </div>
                <span className="field-hint">预警线 70% · 以全部资金账户聚合</span>
              </div>
            </AsyncState>
          </Card>

          <Card title="快捷操作">
            <div className="quick-list">
              <button type="button" className="quick-item" onClick={() => navigate('/trading')}>
                新建委托<span className="chev">→</span>
              </button>
              <button type="button" className="quick-item" onClick={() => navigate('/fund')}>
                资金划转<span className="chev">→</span>
              </button>
              <button type="button" className="quick-item" onClick={() => navigate('/risk')}>
                风控规则<span className="chev">→</span>
              </button>
              <button type="button" className="quick-item" onClick={() => navigate('/monitor')}>
                告警中心<span className="chev">→</span>
              </button>
            </div>
          </Card>
        </div>
      </div>

      <Card
        title="在途委托"
        padded={false}
        actions={
          <>
            <span className="muted">
              {orderRows.length} 笔在途 · 今日成交 {data?.orders.filledToday ?? '-'} 笔
            </span>
            <Button size="sm" variant="ghost" onClick={() => navigate('/trading')}>
              委托列表
            </Button>
          </>
        }
      >
        <AsyncState
          loading={orders.loading}
          error={orders.error}
          onRetry={orders.reload}
          empty={
            !orders.loading &&
            orderRows.length === 0 &&
            '暂无在途委托。提交委托后，未成交部分会显示在这里。'
          }
        >
          <DataTable
            columns={[
              { key: 'createTime', title: '时间' },
              { key: 'orderId', title: '委托号' },
              { key: 'symbol', title: '合约' },
              { key: 'direction', title: '方向' },
              { key: 'price', title: '价格', align: 'right', render: (row: OrderView) => money(row.price) },
              { key: 'quantity', title: '数量', align: 'right', render: (row: OrderView) => qty(row.quantity) },
              {
                key: 'filledQuantity',
                title: '已成交',
                align: 'right',
                render: (row: OrderView) => qty(row.filledQuantity),
              },
              {
                key: 'status',
                title: '状态',
                render: (row: OrderView) => (
                  <Badge tone={statusTone(row.status)}>{row.status ?? '-'}</Badge>
                ),
              },
            ]}
            rows={orderRows.slice(0, 5)}
            rowKey={(row: OrderView) => row.orderId}
            empty="暂无在途委托"
          />
        </AsyncState>
      </Card>

      <Card
        title="最近动态"
        padded={false}
        actions={
          <>
            <span className="muted">委托 / 成交 / 告警</span>
            <Button size="sm" variant="ghost" onClick={timeline.reload}>
              刷新
            </Button>
          </>
        }
      >
        <AsyncState
          loading={timeline.loading}
          error={timeline.error}
          onRetry={timeline.reload}
          empty={!timeline.loading && timelineRows.length === 0 && '暂无动态。'}
        >
          <ol className="timeline">
            {timelineRows.map((item) => (
              <li className="timeline-item" key={`${item.type}-${item.id}`}>
                <span className="timeline-dot" aria-hidden="true" />
                <div>
                  <div className="timeline-title">
                    <Badge tone={statusTone(item.status)}>{item.type}</Badge>
                    <span>{item.title}</span>
                  </div>
                  <div className="timeline-time">
                    {item.at ?? '-'} · {item.status ?? '-'}
                    {item.severity ? ` · ${item.severity}` : ''}
                  </div>
                </div>
              </li>
            ))}
          </ol>
        </AsyncState>
      </Card>
    </Page>
  );
}
