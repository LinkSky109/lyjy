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

/** 平台账本持仓（/api/trade/platform/positions） */
interface PositionView {
  id?: number;
  accountId: string;
  strategyId?: string;
  symbol: string;
  direction: string;
  quantity?: number;
  availableQuantity?: number;
  avgCost?: number;
  unrealizedPnl?: number;
}

/** 订单（/api/trade/orders/{id} 与 /api/oms/orders/active） */
interface OrderView {
  orderId: string;
  accountId?: string;
  strategyId?: string;
  symbol?: string;
  direction?: string;
  offsetFlag?: string;
  orderType?: string;
  price?: number;
  quantity?: number;
  filledQuantity?: number;
  avgFillPrice?: number;
  status?: string;
  exchangeOrderId?: string;
  gatewayId?: string;
  brokerOrderRef?: string;
  errorCode?: string;
  errorMessage?: string;
  createTime?: string;
  submittedAt?: string;
  filledAt?: string;
  canceledAt?: string;
}

/** 统一下单结果（POST /api/trade/orders） */
interface TradeSubmissionView {
  orderId: string;
  status: string;
  brokerOrderRef?: string;
  gatewayId?: string;
  fallbackUsed?: boolean;
  switchedFrom?: string;
  mode?: string;
  replay?: boolean;
}

/** 网关/交易连接状态（GET /api/trade/status） */
interface TradeStatusView {
  connected?: boolean;
  authenticated?: boolean;
  mode?: string;
}

type Notice = { tone: Tone; text: string } | null;

const DIRECTIONS = ['BUY', 'SELL'];
const OFFSET_FLAGS = ['OPEN', 'CLOSE', 'CLOSE_TODAY', 'CLOSE_YESTERDAY'];
const ORDER_TYPES = ['LIMIT', 'MARKET', 'FAK', 'FOK'];

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

function genKey(): string {
  return `ui-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`;
}

export default function TradingPage() {
  const [tab, setTab] = useState('order');

  // 查询条件：草稿 + 已应用
  const [accountDraft, setAccountDraft] = useState('DEFAULT-001');
  const [strategyDraft, setStrategyDraft] = useState('');
  const [accountId, setAccountId] = useState('DEFAULT-001');
  const [strategyId, setStrategyId] = useState('');

  const account = useApi(
    () => api.get<FundAccountView>('/api/trade/platform/account', { accountId }),
    [accountId],
  );
  const positions = useApi(
    () =>
      api.get<PositionView[]>('/api/trade/platform/positions', { accountId, strategyId }),
    [accountId, strategyId],
  );
  const gateway = useApi(() => api.get<TradeStatusView>('/api/trade/status'), []);
  const activeOrders = useApi(() => api.get<OrderView[]>('/api/oms/orders/active'), []);

  // 下单表单
  const [symbol, setSymbol] = useState('');
  const [exchange, setExchange] = useState('');
  const [direction, setDirection] = useState('BUY');
  const [offsetFlag, setOffsetFlag] = useState('OPEN');
  const [orderType, setOrderType] = useState('LIMIT');
  const [price, setPrice] = useState('');
  const [quantity, setQuantity] = useState('');
  const [idempotencyKey, setIdempotencyKey] = useState(genKey);
  const [remark, setRemark] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [orderNotice, setOrderNotice] = useState<Notice>(null);
  const [lastOrder, setLastOrder] = useState<TradeSubmissionView | null>(null);

  const applyQuery = () => {
    setAccountId(accountDraft.trim());
    setStrategyId(strategyDraft.trim());
  };

  const reloadAll = () => {
    account.reload();
    positions.reload();
    activeOrders.reload();
  };

  // 持仓/订单自动刷新（10s），方便盯盘
  const [autoTradeRefresh, setAutoTradeRefresh] = useState(false);
  useEffect(() => {
    if (!autoTradeRefresh) {
      return;
    }
    const timer = setInterval(() => {
      positions.reload();
      activeOrders.reload();
    }, 10000);
    return () => clearInterval(timer);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [autoTradeRefresh, accountId, strategyId]);

  const submitOrder = async () => {
    setOrderNotice(null);
    if (!symbol.trim()) {
      setOrderNotice({ tone: 'danger', text: '请填写标的代码 symbol' });
      return;
    }
    const qty = toNum(quantity);
    if (qty === undefined || qty <= 0) {
      setOrderNotice({ tone: 'danger', text: '请填写大于 0 的委托数量 quantity' });
      return;
    }
    setSubmitting(true);
    try {
      const orderPrice = toNum(price);
      const result = await api.post<TradeSubmissionView>('/api/trade/orders', {
        accountId: accountId.trim() || undefined,
        strategyId: strategyId.trim() || undefined,
        symbol: symbol.trim(),
        exchange: exchange.trim() || undefined,
        direction,
        offsetFlag,
        orderType,
        price: orderPrice,
        quantity: qty,
        idempotencyKey: idempotencyKey.trim() || undefined,
        remark: remark.trim() || undefined,
      });
      setLastOrder(result);
      setOrderNotice({
        tone: 'ok',
        text: `下单成功 orderId=${result.orderId} status=${result.status}${
          result.mode ? ` mode=${result.mode}` : ''
        }${result.replay ? '（幂等重放）' : ''}`,
      });
      setIdempotencyKey(genKey());
      reloadAll();
    } catch (error) {
      setOrderNotice({ tone: 'danger', text: `下单失败：${errorText(error)}` });
    } finally {
      setSubmitting(false);
    }
  };

  // 订单查询
  const [queryOrderId, setQueryOrderId] = useState('');
  const [queryLoading, setQueryLoading] = useState(false);
  const [queryError, setQueryError] = useState<string | null>(null);
  const [queriedOrder, setQueriedOrder] = useState<OrderView | null>(null);
  const [cancelReason, setCancelReason] = useState('用户撤单');

  const loadOrder = async () => {
    const id = queryOrderId.trim();
    setQueryError(null);
    if (!id) {
      setQueryError('请输入 orderId');
      setQueriedOrder(null);
      return;
    }
    setQueryLoading(true);
    try {
      const order = await api.get<OrderView>(`/api/trade/orders/${encodeURIComponent(id)}`);
      setQueriedOrder(order);
    } catch (error) {
      setQueriedOrder(null);
      setQueryError(errorText(error));
    } finally {
      setQueryLoading(false);
    }
  };

  const cancelOrder = async (id: string) => {
    setOrderNotice(null);
    try {
      const order = await api.del<OrderView>(
        `/api/trade/orders/${encodeURIComponent(id)}`,
        { reason: cancelReason.trim() || '用户撤单' },
      );
      setOrderNotice({
        tone: 'ok',
        text: `撤单成功 orderId=${order.orderId} status=${order.status}`,
      });
      if (queriedOrder && queriedOrder.orderId === id) {
        setQueriedOrder(order);
      }
      activeOrders.reload();
    } catch (error) {
      setOrderNotice({ tone: 'danger', text: `撤单失败：${errorText(error)}` });
    }
  };

  const accountData = account.data;
  const positionRows = positions.data ?? [];
  const orderRows = activeOrders.data ?? [];

  return (
    <Page
      title="交易与资金"
      subtitle="统一下单、平台持仓与 OMS 活跃订单管理"
      actions={<Button onClick={reloadAll}>刷新全部</Button>}
    >
      <Grid columns={3}>
        <Stat
          label="网关连接"
          value={gateway.data ? (gateway.data.connected ? '已连接' : '未连接') : '-'}
          tone={gateway.data?.connected ? 'ok' : 'danger'}
          hint={gateway.error ?? undefined}
        />
        <Stat
          label="柜台认证"
          value={gateway.data ? (gateway.data.authenticated ? '已认证' : '未认证') : '-'}
          tone={gateway.data?.authenticated ? 'ok' : 'warn'}
        />
        <Stat label="运行模式" value={gateway.data?.mode ?? '-'} tone="info" />
      </Grid>

      <Card title="查询条件">
        <Toolbar>
          <Field label="账户 ID（accountId）">
            <Input
              value={accountDraft}
              onChange={(event) => setAccountDraft(event.target.value)}
              placeholder="DEFAULT-001"
            />
          </Field>
          <Field label="策略 ID（strategyId，可选）">
            <Input
              value={strategyDraft}
              onChange={(event) => setStrategyDraft(event.target.value)}
              placeholder="留空查询全部"
            />
          </Field>
          <Button variant="primary" onClick={applyQuery}>
            查询
          </Button>
          <label className="muted" style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}>
            <input
              type="checkbox"
              checked={autoTradeRefresh}
              onChange={(event) => setAutoTradeRefresh(event.target.checked)}
            />
            自动刷新（10s）
          </label>
        </Toolbar>
      </Card>

      <Card>
        <Tabs
          active={tab}
          onChange={setTab}
          tabs={[
            { key: 'order', label: '下单' },
            { key: 'positions', label: '持仓' },
            { key: 'orders', label: '订单' },
          ]}
        />

        {tab === 'order' && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
            {orderNotice && <Notice tone={orderNotice.tone}>{orderNotice.text}</Notice>}
            <Grid columns={3}>
              <Field label="标的代码 symbol">
                <Input
                  value={symbol}
                  onChange={(event) => setSymbol(event.target.value)}
                  placeholder="rb2601"
                />
              </Field>
              <Field label="交易所 exchange（可选）">
                <Input
                  value={exchange}
                  onChange={(event) => setExchange(event.target.value)}
                  placeholder="SHFE"
                />
              </Field>
              <Field label="方向 direction">
                <Select value={direction} onChange={(event) => setDirection(event.target.value)}>
                  {DIRECTIONS.map((item) => (
                    <option key={item} value={item}>
                      {item}
                    </option>
                  ))}
                </Select>
              </Field>
              <Field label="开平标志 offsetFlag">
                <Select
                  value={offsetFlag}
                  onChange={(event) => setOffsetFlag(event.target.value)}
                >
                  {OFFSET_FLAGS.map((item) => (
                    <option key={item} value={item}>
                      {item}
                    </option>
                  ))}
                </Select>
              </Field>
              <Field label="订单类型 orderType">
                <Select value={orderType} onChange={(event) => setOrderType(event.target.value)}>
                  {ORDER_TYPES.map((item) => (
                    <option key={item} value={item}>
                      {item}
                    </option>
                  ))}
                </Select>
              </Field>
              <Field label="委托价格 price（市价可留空）">
                <Input
                  value={price}
                  onChange={(event) => setPrice(event.target.value)}
                  placeholder="3500"
                />
              </Field>
              <Field label="委托数量 quantity">
                <Input
                  value={quantity}
                  onChange={(event) => setQuantity(event.target.value)}
                  placeholder="1"
                />
              </Field>
              <Field label="幂等键 idempotencyKey">
                <Input
                  value={idempotencyKey}
                  onChange={(event) => setIdempotencyKey(event.target.value)}
                />
              </Field>
              <Field label="账户 ID accountId（来自查询区）">
                <Input value={accountId} readOnly />
              </Field>
            </Grid>
            <Field label="备注 remark（可选）">
              <Input
                value={remark}
                onChange={(event) => setRemark(event.target.value)}
                placeholder="手工下单"
              />
            </Field>
            <Toolbar>
              <Button variant="primary" onClick={submitOrder} disabled={submitting}>
                {submitting ? '提交中…' : '提交下单'}
              </Button>
              <Button onClick={() => setIdempotencyKey(genKey())}>重新生成幂等键</Button>
            </Toolbar>
            {lastOrder && (
              <Card title="最近下单结果">
                <KeyValue
                  items={[
                    { label: 'orderId', value: lastOrder.orderId },
                    { label: 'status', value: <Badge tone={statusToneOf(lastOrder.status)}>{lastOrder.status}</Badge> },
                    { label: 'mode', value: lastOrder.mode ?? '-' },
                    { label: 'gatewayId', value: lastOrder.gatewayId ?? '-' },
                    { label: 'brokerOrderRef', value: lastOrder.brokerOrderRef ?? '-' },
                    { label: '幂等重放', value: lastOrder.replay ? '是' : '否' },
                    { label: '降级切换', value: lastOrder.fallbackUsed ? lastOrder.switchedFrom ?? '是' : '否' },
                  ]}
                />
                <JsonBlock value={lastOrder} />
              </Card>
            )}
          </div>
        )}

        {tab === 'positions' && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
            <AsyncState
              loading={account.loading}
              error={account.error}
              onRetry={account.reload}
            >
              {accountData && (
                <>
                  <Grid columns={4}>
                    <Stat label="总资产 totalAsset" value={fmt(accountData.totalAsset)} tone="info" />
                    <Stat
                      label="可用资金 availableCash"
                      value={fmt(accountData.availableCash)}
                      tone="ok"
                    />
                    <Stat
                      label="冻结资金 frozenCash"
                      value={fmt(accountData.frozenCash)}
                      tone="warn"
                    />
                    <Stat label="保证金 margin" value={fmt(accountData.margin)} tone="warn" />
                    <Stat
                      label="浮动盈亏 unrealizedPnl"
                      value={fmt(accountData.unrealizedPnl)}
                      tone={(accountData.unrealizedPnl ?? 0) >= 0 ? 'ok' : 'danger'}
                    />
                    <Stat
                      label="已实现盈亏 realizedPnl"
                      value={fmt(accountData.realizedPnl)}
                      tone={(accountData.realizedPnl ?? 0) >= 0 ? 'ok' : 'danger'}
                    />
                    <Stat label="账户名称" value={accountData.accountName ?? '-'} tone="muted" />
                    <Stat label="币种" value={accountData.currency ?? '-'} tone="muted" />
                  </Grid>
                  <KeyValue
                    items={[
                      { label: 'accountId', value: accountData.accountId },
                      { label: 'accountType', value: accountData.accountType ?? '-' },
                    ]}
                  />
                </>
              )}
            </AsyncState>

            <AsyncState
              loading={positions.loading}
              error={positions.error}
              onRetry={positions.reload}
            >
              <DataTable
                columns={[
                  { key: 'symbol', title: '标的' },
                  { key: 'strategyId', title: '策略' },
                  {
                    key: 'direction',
                    title: '方向',
                    render: (row: PositionView) => (
                      <Badge tone={row.direction === 'BUY' || row.direction === 'LONG' ? 'ok' : 'danger'}>
                        {row.direction}
                      </Badge>
                    ),
                  },
                  { key: 'quantity', title: '数量', align: 'right', render: (row: PositionView) => fmt(row.quantity) },
                  {
                    key: 'availableQuantity',
                    title: '可用数量',
                    align: 'right',
                    render: (row: PositionView) => fmt(row.availableQuantity),
                  },
                  { key: 'avgCost', title: '持仓均价', align: 'right', render: (row: PositionView) => fmt(row.avgCost) },
                  {
                    key: 'unrealizedPnl',
                    title: '浮动盈亏',
                    align: 'right',
                    render: (row: PositionView) => (
                      <span style={{ color: (row.unrealizedPnl ?? 0) >= 0 ? 'var(--ok)' : 'var(--danger)' }}>
                        {fmt(row.unrealizedPnl)}
                      </span>
                    ),
                  },
                ]}
                rows={positionRows}
                rowKey={(row: PositionView) =>
                  `${row.accountId}-${row.strategyId ?? ''}-${row.symbol}-${row.direction}-${row.id ?? ''}`
                }
                empty="暂无持仓"
              />
            </AsyncState>
          </div>
        )}

        {tab === 'orders' && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
            <Toolbar>
              <Field label="平台订单号 orderId">
                <Input
                  value={queryOrderId}
                  onChange={(event) => setQueryOrderId(event.target.value)}
                  placeholder="输入订单号查询"
                />
              </Field>
              <Field label="撤单原因 reason">
                <Input
                  value={cancelReason}
                  onChange={(event) => setCancelReason(event.target.value)}
                />
              </Field>
              <Button variant="primary" onClick={loadOrder} disabled={queryLoading}>
                {queryLoading ? '查询中…' : '查询订单'}
              </Button>
              <Button onClick={() => activeOrders.reload()}>刷新活跃订单</Button>
            </Toolbar>

            {queryError && <Notice tone="danger">{queryError}</Notice>}

            {queriedOrder && (
              <Card
                title="订单详情"
                actions={
                  <Button variant="danger" size="sm" onClick={() => cancelOrder(queriedOrder.orderId)}>
                    撤单
                  </Button>
                }
              >
                <OrderFields order={queriedOrder} />
                <JsonBlock value={queriedOrder} />
              </Card>
            )}

            <Card title="活跃订单">
              <AsyncState
                loading={activeOrders.loading}
                error={activeOrders.error}
                onRetry={activeOrders.reload}
              >
                <DataTable
                  columns={[
                    { key: 'orderId', title: '订单号' },
                    { key: 'symbol', title: '标的' },
                    { key: 'direction', title: '方向' },
                    { key: 'offsetFlag', title: '开平' },
                    { key: 'orderType', title: '类型' },
                    { key: 'price', title: '价格', align: 'right', render: (row: OrderView) => fmt(row.price) },
                    { key: 'quantity', title: '数量', align: 'right', render: (row: OrderView) => fmt(row.quantity) },
                    {
                      key: 'filledQuantity',
                      title: '已成交',
                      align: 'right',
                      render: (row: OrderView) => fmt(row.filledQuantity),
                    },
                    {
                      key: 'status',
                      title: '状态',
                      render: (row: OrderView) => (
                        <Badge tone={statusToneOf(row.status)}>{row.status ?? '-'}</Badge>
                      ),
                    },
                    {
                      key: 'action',
                      title: '操作',
                      render: (row: OrderView) => (
                        <Button variant="danger" size="sm" onClick={() => cancelOrder(row.orderId)}>
                          撤单
                        </Button>
                      ),
                    },
                  ]}
                  rows={orderRows}
                  rowKey={(row: OrderView) => row.orderId}
                  empty="暂无活跃订单"
                />
              </AsyncState>
            </Card>
          </div>
        )}
      </Card>
    </Page>
  );
}

function OrderFields({ order }: { order: OrderView }) {
  return (
    <KeyValue
      items={[
        { label: 'orderId', value: order.orderId },
        { label: 'status', value: <Badge tone={statusToneOf(order.status)}>{order.status ?? '-'}</Badge> },
        { label: 'symbol', value: order.symbol ?? '-' },
        { label: 'direction', value: order.direction ?? '-' },
        { label: 'offsetFlag', value: order.offsetFlag ?? '-' },
        { label: 'orderType', value: order.orderType ?? '-' },
        { label: 'price', value: fmt(order.price) },
        { label: 'quantity', value: fmt(order.quantity) },
        { label: 'filledQuantity', value: fmt(order.filledQuantity) },
        { label: 'avgFillPrice', value: fmt(order.avgFillPrice) },
        { label: 'exchangeOrderId', value: order.exchangeOrderId ?? '-' },
        { label: 'gatewayId', value: order.gatewayId ?? '-' },
        { label: 'errorMessage', value: order.errorMessage ?? '-' },
        { label: 'createTime', value: order.createTime ?? '-' },
      ]}
    />
  );
}

/** 订单/结果状态到 Tone 的映射（复用后端状态枚举） */
function statusToneOf(value: string | undefined): Tone {
  const text = (value ?? '').toUpperCase();
  if (['FILLED', 'SUBMITTED', 'ACCEPTED', 'SUCCESS', 'COMPLETED', 'ACTIVE'].includes(text)) {
    return 'ok';
  }
  if (['PENDING', 'PARTIAL_FILLED', 'PARTIALLY_FILLED', 'OPEN', 'PENDING_NEW'].includes(text)) {
    return 'warn';
  }
  if (['REJECTED', 'CANCELED', 'CANCELLED', 'FAILED', 'ERROR', 'TIMEOUT'].includes(text)) {
    return 'danger';
  }
  return 'muted';
}
