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
  cancelable?: boolean;
  gatewayAckStatus?: string;
  createTime?: string;
  submittedAt?: string;
  filledAt?: string;
  canceledAt?: string;
}

/** 成交回报（/api/trade/trades） */
interface TradeReportView {
  tradeId?: string;
  clientOrderId?: string;
  symbol?: string;
  direction?: string;
  offsetFlag?: string;
  price?: number;
  volume?: number;
  turnover?: number;
  tradeTime?: string;
  tradingDay?: string;
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

/** 合约元数据（GET /api/trade/instruments/{symbol}） */
interface InstrumentView {
  symbol: string;
  name?: string;
  exchange?: string;
  instrumentType?: string;
  priceTick?: number;
  contractMultiplier?: number;
  currency?: string;
  minQty?: number;
  maxQty?: number;
  qtyStep?: number;
  limitUp?: number;
  limitDown?: number;
  trading?: boolean;
}

/** 最新行情快照（GET /api/market/quote/{symbol}），含五档盘口 */
interface TickView {
  symbol?: string;
  lastPrice?: number;
  preClose?: number;
  open?: number;
  high?: number;
  low?: number;
  volume?: number;
  turnover?: number;
  bidPrice1?: number;
  bidPrice2?: number;
  bidPrice3?: number;
  bidPrice4?: number;
  bidPrice5?: number;
  bidVolume1?: number;
  bidVolume2?: number;
  bidVolume3?: number;
  bidVolume4?: number;
  bidVolume5?: number;
  askPrice1?: number;
  askPrice2?: number;
  askPrice3?: number;
  askPrice4?: number;
  askPrice5?: number;
  askVolume1?: number;
  askVolume2?: number;
  askVolume3?: number;
  askVolume4?: number;
  askVolume5?: number;
  tickTime?: string;
  recvTime?: string;
  staleMs?: number;
}

interface PageResult<T> {
  items: T[];
  total: number;
  page: number;
  size: number;
}

type NoticeState = { tone: Tone; text: string } | null;

const DIRECTIONS = ['BUY', 'SELL'];
const OFFSET_FLAGS = ['OPEN', 'CLOSE', 'CLOSE_TODAY', 'CLOSE_YESTERDAY'];
const ORDER_TYPES = ['LIMIT', 'MARKET', 'FAK', 'FOK'];
const MAX_QUANTITY = 200;

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

function directionLabel(value: string): string {
  return value === 'BUY' ? '买入' : value === 'SELL' ? '卖出' : value;
}

export default function TradingPage() {
  const [tab, setTab] = useState('orders');

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
    () => api.get<PositionView[]>('/api/trade/platform/positions', { accountId, strategyId }),
    [accountId, strategyId],
  );
  const gateway = useApi(() => api.get<TradeStatusView>('/api/trade/status'), []);
  const [orderStatus, setOrderStatus] = useState('');
  const [orderPage, setOrderPage] = useState(1);
  const ORDER_PAGE_SIZE = 10;
  const ordersPage = useApi<PageResult<OrderView>>(
    () =>
      api.get<PageResult<OrderView>>('/api/oms/orders', {
        status: orderStatus || undefined,
        page: orderPage,
        size: ORDER_PAGE_SIZE,
      }),
    [orderStatus, orderPage],
  );
  const trades = useApi(() => api.get<TradeReportView[]>('/api/trade/trades'), []);

  // 下单表单
  const [symbol, setSymbol] = useState('');
  const [quoteSymbol, setQuoteSymbol] = useState('');
  const [exchange, setExchange] = useState('');
  const [direction, setDirection] = useState('BUY');
  const [offsetFlag, setOffsetFlag] = useState('OPEN');
  const [orderType, setOrderType] = useState('LIMIT');
  const [price, setPrice] = useState('');
  const [quantity, setQuantity] = useState('');
  const [idempotencyKey, setIdempotencyKey] = useState(genKey);
  const [remark, setRemark] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [orderNotice, setOrderNotice] = useState<NoticeState>(null);
  const [lastOrder, setLastOrder] = useState<TradeSubmissionView | null>(null);
  const [formErrors, setFormErrors] = useState<{
    symbol?: string;
    price?: string;
    quantity?: string;
  }>({});
  const [confirmOpen, setConfirmOpen] = useState(false);

  const quote = useApi<TickView | null>(
    () =>
      quoteSymbol
        ? api.get<TickView>(`/api/market/quote/${encodeURIComponent(quoteSymbol)}`)
        : Promise.resolve(null),
    [quoteSymbol],
  );

  const instrument = useApi<InstrumentView | null>(
    () =>
      quoteSymbol
        ? api
            .get<InstrumentView>(`/api/trade/instruments/${encodeURIComponent(quoteSymbol)}`)
            .catch(() => null)
        : Promise.resolve(null),
    [quoteSymbol],
  );

  const applyQuery = () => {
    setAccountId(accountDraft.trim());
    setStrategyId(strategyDraft.trim());
  };

  const reloadAll = () => {
    account.reload();
    positions.reload();
    ordersPage.reload();
    trades.reload();
    quote.reload();
  };

  // 持仓/订单自动刷新（10s），方便盯盘
  const [autoTradeRefresh, setAutoTradeRefresh] = useState(false);
  useEffect(() => {
    if (!autoTradeRefresh) {
      return;
    }
    const timer = setInterval(() => {
      positions.reload();
      ordersPage.reload();
      trades.reload();
      quote.reload();
    }, 10000);
    return () => clearInterval(timer);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [autoTradeRefresh, accountId, strategyId, quoteSymbol]);

  const validate = (): boolean => {
    const errors: typeof formErrors = {};
    if (!symbol.trim()) {
      errors.symbol = '请填写标的代码，例如 rb2601 或 BTC_USDT。';
    }
    const qty = toNum(quantity);
    const maxQty = instrument.data?.maxQty ?? MAX_QUANTITY;
    const minQty = instrument.data?.minQty ?? 1;
    if (qty === undefined) {
      errors.quantity = '请填写委托数量（整数）。';
    } else if (!Number.isInteger(qty) || qty <= 0) {
      errors.quantity = '数量必须为正整数。';
    } else if (qty < minQty) {
      errors.quantity = `数量不得小于 ${minQty}。`;
    } else if (qty > maxQty) {
      errors.quantity = `单笔数量不得超过 ${maxQty}，请拆单后重试。`;
    }
    const priceValue = toNum(price);
    if (orderType === 'MARKET') {
      if (price.trim() !== '' && (priceValue === undefined || priceValue <= 0)) {
        errors.price = '市价单价格可留空；如填写必须大于 0。';
      }
    } else if (priceValue === undefined || priceValue <= 0) {
      errors.price = '限价单必须填写大于 0 的委托价格。';
    }
    setFormErrors(errors);
    return Object.keys(errors).length === 0;
  };

  const openConfirm = () => {
    setOrderNotice(null);
    if (validate()) {
      setConfirmOpen(true);
    }
  };

  const submitOrder = async () => {
    setSubmitting(true);
    try {
      const qty = toNum(quantity) as number;
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
      setConfirmOpen(false);
      setQuoteSymbol(symbol.trim());
      reloadAll();
    } catch (error) {
      setOrderNotice({ tone: 'danger', text: `下单失败：${errorText(error)}` });
      setConfirmOpen(false);
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
      const order = await api.del<OrderView>(`/api/trade/orders/${encodeURIComponent(id)}`, {
        reason: cancelReason.trim() || '用户撤单',
      });
      setOrderNotice({
        tone: 'ok',
        text: `撤单成功 orderId=${order.orderId} status=${order.status}`,
      });
      if (queriedOrder && queriedOrder.orderId === id) {
        setQueriedOrder(order);
      }
      ordersPage.reload();
      trades.reload();
    } catch (error) {
      setOrderNotice({ tone: 'danger', text: `撤单失败：${errorText(error)}` });
    }
  };

  const accountData = account.data;
  const positionRows = positions.data ?? [];
  const orderRows = ordersPage.data?.items ?? [];
  const orderTotal = ordersPage.data?.total ?? 0;
  const orderPages = Math.max(1, Math.ceil(orderTotal / ORDER_PAGE_SIZE));
  const tradeRows = trades.data ?? [];
  const tick = quote.data;
  const last = tick?.lastPrice;
  const prev = tick?.preClose;
  const diff = last !== undefined && prev !== undefined ? last - prev : undefined;
  const diffPct = diff !== undefined && prev ? (diff / prev) * 100 : undefined;

  const multiplier = instrument.data?.contractMultiplier ?? 1;
  const notional =
    toNum(price) !== undefined && toNum(quantity) !== undefined
      ? (toNum(price) as number) * (toNum(quantity) as number) * multiplier
      : undefined;

  const bidRows = [5, 4, 3, 2, 1]
    .map((level) => ({
      level,
      price: tick?.[`bidPrice${level}` as keyof TickView] as number | undefined,
      volume: tick?.[`bidVolume${level}` as keyof TickView] as number | undefined,
    }))
    .filter((row) => row.price !== undefined);

  const askRows = [1, 2, 3, 4, 5]
    .map((level) => ({
      level,
      price: tick?.[`askPrice${level}` as keyof TickView] as number | undefined,
      volume: tick?.[`askVolume${level}` as keyof TickView] as number | undefined,
    }))
    .filter((row) => row.price !== undefined);

  return (
    <Page
      title="交易下单"
      subtitle="限价 / 市价委托，提交前经过事前风控校验；模拟环境不触达真实柜台。"
      actions={<Button onClick={reloadAll}>刷新全部</Button>}
    >
      <div className="status-strip">
        <div className="status-item">
          <span className="status-label">网关连接</span>
          <span className="status-value">
            <span
              className={`dot ${
                gateway.data?.connected === undefined
                  ? ''
                  : gateway.data.connected
                    ? 'dot-ok'
                    : 'dot-danger'
              }`}
            />
            {gateway.data ? (gateway.data.connected ? '已连接' : '未连接') : '-'}
          </span>
        </div>
        <div className="status-item">
          <span className="status-label">柜台认证</span>
          <span className="status-value">
            <span
              className={`dot ${
                gateway.data?.authenticated === undefined
                  ? ''
                  : gateway.data.authenticated
                    ? 'dot-ok'
                    : 'dot-warn'
              }`}
            />
            {gateway.data ? (gateway.data.authenticated ? '已认证' : '未认证') : '-'}
          </span>
        </div>
        <div className="status-item">
          <span className="status-label">运行模式</span>
          <span className="status-value">{gateway.data?.mode ?? '-'}</span>
        </div>
        <div className="status-item">
          <span className="status-label">可用资金</span>
          <span className="status-value num">
            {accountData ? `¥${fmt(accountData.availableCash)}` : '-'}
          </span>
        </div>
      </div>

      <Card title="账户上下文" actions={<span className="muted">查询条件</span>}>
        <Toolbar>
          <Field label="策略 ID（strategyId，可选）">
            <Input
              value={strategyDraft}
              onChange={(event) => setStrategyDraft(event.target.value)}
              placeholder="留空查询全部"
            />
          </Field>
          <Button onClick={applyQuery}>查询</Button>
          <label className="checkline">
            <input
              type="checkbox"
              checked={autoTradeRefresh}
              onChange={(event) => setAutoTradeRefresh(event.target.checked)}
            />
            自动刷新（10s）
          </label>
        </Toolbar>
      </Card>

      <div className="split">
        <Card title="委托下单" actions={<span className="muted">事前风控已启用</span>}>
          {orderNotice && <Notice tone={orderNotice.tone} live>{orderNotice.text}</Notice>}
          <Field label="资金账户 accountId">
            <Input
              value={accountDraft}
              onChange={(event) => setAccountDraft(event.target.value)}
              onBlur={() => setAccountId(accountDraft.trim() || 'DEFAULT-001')}
              placeholder="DEFAULT-001"
            />
          </Field>
          <div className="field">
            <label className="field-label" htmlFor="order-symbol">
              标的代码
            </label>
            <Input
              id="order-symbol"
              value={symbol}
              onChange={(event) => setSymbol(event.target.value)}
              onBlur={() => setQuoteSymbol(symbol.trim())}
              placeholder="rb2601 / BTC_USDT"
              aria-invalid={formErrors.symbol ? 'true' : undefined}
              aria-describedby="order-symbol-error"
            />
            {formErrors.symbol && (
              <span className="field-error" id="order-symbol-error" role="alert">
                {formErrors.symbol}
              </span>
            )}
          </div>

          <Grid columns={2}>
            <Field label="交易所 exchange（可选）">
              <Input
                value={exchange}
                onChange={(event) => setExchange(event.target.value)}
                placeholder="SHFE"
              />
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
          </Grid>

          <Grid columns={2}>
            <div className="field">
              <span className="field-label" id="direction-label">
                方向
              </span>
              <div
                className={`segmented ${direction === 'BUY' ? 'seg-buy' : 'seg-sell'}`}
                role="radiogroup"
                aria-labelledby="direction-label"
              >
                {DIRECTIONS.map((item) => (
                  <span key={item} style={{ display: 'contents' }}>
                    <input
                      type="radio"
                      id={`dir-${item}`}
                      name="direction"
                      value={item}
                      checked={direction === item}
                      onChange={() => setDirection(item)}
                    />
                    <label htmlFor={`dir-${item}`}>{directionLabel(item)}</label>
                  </span>
                ))}
              </div>
            </div>
            <Field label="开平标志 offsetFlag">
              <Select value={offsetFlag} onChange={(event) => setOffsetFlag(event.target.value)}>
                {OFFSET_FLAGS.map((item) => (
                  <option key={item} value={item}>
                    {item}
                  </option>
                ))}
              </Select>
            </Field>
          </Grid>

          <Grid columns={2}>
            <div className="field">
              <label className="field-label" htmlFor="order-price">
                委托价格 price{orderType === 'MARKET' ? '（市价可留空）' : ''}
              </label>
              <Input
                id="order-price"
                value={price}
                onChange={(event) => setPrice(event.target.value)}
                placeholder="3500"
                inputMode="decimal"
                aria-invalid={formErrors.price ? 'true' : undefined}
                aria-describedby="order-price-error"
              />
              {formErrors.price && (
                <span className="field-error" id="order-price-error" role="alert">
                  {formErrors.price}
                </span>
              )}
            </div>
            <div className="field">
              <label className="field-label" htmlFor="order-qty">
                委托数量 quantity（≤ {instrument.data?.maxQty ?? MAX_QUANTITY}）
              </label>
              <Input
                id="order-qty"
                value={quantity}
                onChange={(event) => setQuantity(event.target.value)}
                placeholder="1"
                inputMode="numeric"
                aria-invalid={formErrors.quantity ? 'true' : undefined}
                aria-describedby="order-qty-error"
              />
              {formErrors.quantity && (
                <span className="field-error" id="order-qty-error" role="alert">
                  {formErrors.quantity}
                </span>
              )}
            </div>
          </Grid>

          <div className="summary-box">
            <div className="summary-row">
              <span>
                预估名义价值（价格 × 数量{multiplier !== 1 ? ` × 乘数 ${multiplier}` : '，未配置乘数'}）
              </span>
              <span className="num">{notional === undefined ? '-' : `¥${fmt(notional)}`}</span>
            </div>
            <div className="summary-row">
              <span>可用资金</span>
              <span className="num">{accountData ? `¥${fmt(accountData.availableCash)}` : '-'}</span>
            </div>
            <div className="summary-row">
              <span>账户</span>
              <span className="num">{accountId || '-'}</span>
            </div>
          </div>

          <Grid columns={2}>
            <Field label="幂等键 idempotencyKey">
              <Input
                value={idempotencyKey}
                onChange={(event) => setIdempotencyKey(event.target.value)}
              />
            </Field>
            <Field label="备注 remark（可选）">
              <Input
                value={remark}
                onChange={(event) => setRemark(event.target.value)}
                placeholder="手工下单"
              />
            </Field>
          </Grid>

          <Toolbar>
            <Button variant="primary" onClick={openConfirm} disabled={submitting}>
              {submitting ? '提交中…' : '提交下单'}
            </Button>
            <Button onClick={() => setIdempotencyKey(genKey())}>重新生成幂等键</Button>
          </Toolbar>

          {lastOrder && (
            <Card title="最近下单结果">
              <KeyValue
                items={[
                  { label: 'orderId', value: lastOrder.orderId },
                  {
                    label: 'status',
                    value: <Badge tone={statusToneOf(lastOrder.status)}>{lastOrder.status}</Badge>,
                  },
                  { label: 'mode', value: lastOrder.mode ?? '-' },
                  { label: 'gatewayId', value: lastOrder.gatewayId ?? '-' },
                  { label: 'brokerOrderRef', value: lastOrder.brokerOrderRef ?? '-' },
                  { label: '幂等重放', value: lastOrder.replay ? '是' : '否' },
                  {
                    label: '降级切换',
                    value: lastOrder.fallbackUsed ? lastOrder.switchedFrom ?? '是' : '否',
                  },
                ]}
              />
              <JsonBlock value={lastOrder} />
            </Card>
          )}
        </Card>

        <Card
          title="合约行情"
          actions={
            <Button size="sm" variant="ghost" onClick={() => setQuoteSymbol(symbol.trim())}>
              刷新行情
            </Button>
          }
        >
          <AsyncState
            loading={quote.loading}
            error={quote.error}
            onRetry={quote.reload}
            empty={
              !quoteSymbol
                ? '输入标的代码并离开输入框，或点击「刷新行情」加载五档盘口。'
                : '该标的暂无 tick 数据。确认行情服务已启动且标的已订阅。'
            }
          >
            {tick && (
              <>
                <div className="quote-head">
                  <div>
                    <div className="quote-symbol mono">{tick.symbol ?? quoteSymbol}</div>
                    <div className="quote-name">
                      最新 tick · {tick.tickTime ?? '-'}
                      {tick.staleMs !== undefined && tick.staleMs > 3000
                        ? ` · 延迟 ${Math.round(tick.staleMs / 1000)}s`
                        : ''}
                    </div>
                  </div>
                  <div className="text-right">
                    <div className={`quote-last ${diff === undefined ? '' : diff >= 0 ? 'delta-up' : 'delta-down'}`}>
                      {fmt(last)}
                    </div>
                    <div className="quote-sub">
                      <span className={diff === undefined ? '' : diff >= 0 ? 'delta-up' : 'delta-down'}>
                        {diff === undefined ? '-' : `${diff >= 0 ? '▲' : '▼'} ${fmt(Math.abs(diff))}`}
                      </span>
                      <span className={diff === undefined ? '' : diff >= 0 ? 'delta-up' : 'delta-down'}>
                        {diffPct === undefined ? '-' : `${diffPct >= 0 ? '+' : ''}${diffPct.toFixed(2)}%`}
                      </span>
                      <span>昨收 {fmt(prev)}</span>
                    </div>
                  </div>
                </div>

                <div className="meter mt-4">
                  <div className="meter-head">
                    <span>五档盘口</span>
                    <strong>{tick.volume !== undefined ? `成交量 ${fmt(tick.volume)}` : ''}</strong>
                  </div>
                </div>
                <div className="table-wrap">
                  <table className="table">
                    <thead>
                      <tr>
                        <th scope="col">档位</th>
                        <th scope="col" className="num">
                          价格
                        </th>
                        <th scope="col" className="num">
                          数量
                        </th>
                      </tr>
                    </thead>
                    <tbody>
                      {[...askRows].reverse().map((row) => (
                        <tr key={`ask-${row.level}`}>
                          <td data-label="档位" className="muted">
                            卖 {row.level}
                          </td>
                          <td data-label="价格" className="num delta-up">
                            {fmt(row.price)}
                          </td>
                          <td data-label="数量" className="num">
                            {fmt(row.volume)}
                          </td>
                        </tr>
                      ))}
                      {[...bidRows].map((row) => (
                        <tr key={`bid-${row.level}`}>
                          <td data-label="档位" className="muted">
                            买 {row.level}
                          </td>
                          <td data-label="价格" className="num delta-down">
                            {fmt(row.price)}
                          </td>
                          <td data-label="数量" className="num">
                            {fmt(row.volume)}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
                <dl className="quote-grid">
                  <dt>今开</dt>
                  <dd>{fmt(tick.open)}</dd>
                  <dt>最高 / 最低</dt>
                  <dd>
                    {fmt(tick.high)} / {fmt(tick.low)}
                  </dd>
                  <dt>成交额</dt>
                  <dd>{fmt(tick.turnover)}</dd>
                  <dt>合约乘数</dt>
                  <dd>{instrument.data?.contractMultiplier ?? '未配置'}</dd>
                  <dt>最小变动 / 数量限制</dt>
                  <dd>
                    {instrument.data?.priceTick ?? '-'} / {instrument.data?.maxQty ?? MAX_QUANTITY}
                  </dd>
                </dl>
              </>
            )}
          </AsyncState>
        </Card>
      </div>

      <Card padded={false}>
        <Tabs
          active={tab}
          onChange={setTab}
          tabs={[
            { key: 'orders', label: '委托' },
            { key: 'trades', label: '成交' },
            { key: 'positions', label: '持仓' },
          ]}
        />

        <div className="card-body">
          {tab === 'orders' && (
            <div className="stack">
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
                <Button onClick={loadOrder} disabled={queryLoading}>
                  {queryLoading ? '查询中…' : '查询订单'}
                </Button>
                <Button onClick={() => ordersPage.reload()}>刷新列表</Button>
              </Toolbar>

              {queryError && <Notice tone="danger">{queryError}</Notice>}

              {queriedOrder && (
                <Card
                  title="订单详情"
                  actions={
                    <Button
                      variant="danger"
                      size="sm"
                      onClick={() => cancelOrder(queriedOrder.orderId)}
                    >
                      撤单
                    </Button>
                  }
                >
                  <OrderFields order={queriedOrder} />
                  <JsonBlock value={queriedOrder} />
                </Card>
              )}

              <Toolbar>
                <Field label="状态筛选">
                  <Select
                    value={orderStatus}
                    onChange={(event) => {
                      setOrderStatus(event.target.value);
                      setOrderPage(1);
                    }}
                  >
                    <option value="">全部</option>
                    <option value="SUBMITTED">已提交</option>
                    <option value="ACCEPTED">已接受</option>
                    <option value="PARTIAL_FILLED">部分成交</option>
                    <option value="FILLED">全部成交</option>
                    <option value="CANCELED">已撤销</option>
                    <option value="REJECTED">已拒绝</option>
                  </Select>
                </Field>
                <span className="muted">
                  第 {orderPage} / {orderPages} 页 · 共 {orderTotal} 条
                </span>
                <Button
                  size="sm"
                  disabled={orderPage <= 1}
                  onClick={() => setOrderPage((value) => Math.max(1, value - 1))}
                >
                  上一页
                </Button>
                <Button
                  size="sm"
                  disabled={orderPage >= orderPages}
                  onClick={() => setOrderPage((value) => Math.min(orderPages, value + 1))}
                >
                  下一页
                </Button>
              </Toolbar>

              <AsyncState
                loading={ordersPage.loading}
                error={ordersPage.error}
                onRetry={ordersPage.reload}
                empty={
                  !ordersPage.loading &&
                  orderRows.length === 0 &&
                  '没有匹配的委托。调整状态筛选，或到左侧提交第一笔委托。'
                }
              >
                <DataTable
                  columns={[
                    { key: 'createTime', title: '时间' },
                    { key: 'orderId', title: '订单号' },
                    { key: 'symbol', title: '标的' },
                    { key: 'direction', title: '方向' },
                    { key: 'offsetFlag', title: '开平' },
                    { key: 'orderType', title: '类型' },
                    {
                      key: 'price',
                      title: '价格',
                      align: 'right',
                      render: (row: OrderView) => fmt(row.price),
                    },
                    {
                      key: 'quantity',
                      title: '数量',
                      align: 'right',
                      render: (row: OrderView) => fmt(row.quantity),
                    },
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
                      align: 'right',
                      render: (row: OrderView) =>
                        row.cancelable ? (
                          <Button variant="danger" size="sm" onClick={() => cancelOrder(row.orderId)}>
                            撤单
                          </Button>
                        ) : (
                          <span className="muted" title={`柜台状态：${row.gatewayAckStatus ?? 'NONE'}`}>
                            -
                          </span>
                        ),
                    },
                  ]}
                  rows={orderRows}
                  rowKey={(row: OrderView) => row.orderId}
                  empty="暂无在途委托"
                />
              </AsyncState>
            </div>
          )}

          {tab === 'trades' && (
            <AsyncState
              loading={trades.loading}
              error={trades.error}
              onRetry={trades.reload}
              empty={
                !trades.loading &&
                tradeRows.length === 0 &&
                '暂无成交回报。委托成交后由柜台回报写入，页面不本地伪造成交。'
              }
            >
              <DataTable
                columns={[
                  { key: 'tradeTime', title: '成交时间' },
                  { key: 'tradeId', title: '成交号' },
                  { key: 'clientOrderId', title: '委托号' },
                  { key: 'symbol', title: '合约' },
                  { key: 'direction', title: '方向' },
                  {
                    key: 'price',
                    title: '成交价',
                    align: 'right',
                    render: (row: TradeReportView) => fmt(row.price),
                  },
                  {
                    key: 'volume',
                    title: '数量',
                    align: 'right',
                    render: (row: TradeReportView) => fmt(row.volume),
                  },
                  {
                    key: 'turnover',
                    title: '成交额',
                    align: 'right',
                    render: (row: TradeReportView) => fmt(row.turnover),
                  },
                ]}
                rows={tradeRows}
                rowKey={(row: TradeReportView) => String(row.tradeId ?? row.clientOrderId)}
                empty="暂无成交回报"
              />
            </AsyncState>
          )}

          {tab === 'positions' && (
            <div className="stack">
              <AsyncState loading={account.loading} error={account.error} onRetry={account.reload}>
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
                empty={
                  !positions.loading &&
                  positionRows.length === 0 &&
                  '当前账户没有持仓。可到「委托」页签提交第一笔委托。'
                }
              >
                <DataTable
                  columns={[
                    { key: 'symbol', title: '标的' },
                    { key: 'strategyId', title: '策略' },
                    {
                      key: 'direction',
                      title: '方向',
                      render: (row: PositionView) => (
                        <Badge
                          tone={row.direction === 'BUY' || row.direction === 'LONG' ? 'ok' : 'danger'}
                        >
                          {row.direction}
                        </Badge>
                      ),
                    },
                    {
                      key: 'quantity',
                      title: '数量',
                      align: 'right',
                      render: (row: PositionView) => fmt(row.quantity),
                    },
                    {
                      key: 'availableQuantity',
                      title: '可用数量',
                      align: 'right',
                      render: (row: PositionView) => fmt(row.availableQuantity),
                    },
                    {
                      key: 'avgCost',
                      title: '持仓均价',
                      align: 'right',
                      render: (row: PositionView) => fmt(row.avgCost),
                    },
                    {
                      key: 'unrealizedPnl',
                      title: '浮动盈亏',
                      align: 'right',
                      render: (row: PositionView) => (
                        <span className={(row.unrealizedPnl ?? 0) >= 0 ? 'delta-up' : 'delta-down'}>
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
        </div>
      </Card>

      <Modal
        open={confirmOpen}
        title="确认提交委托"
        onClose={() => setConfirmOpen(false)}
        footer={
          <>
            <Button onClick={() => setConfirmOpen(false)} disabled={submitting}>
              取消
            </Button>
            <Button variant="primary" onClick={submitOrder} disabled={submitting}>
              {submitting ? '提交中…' : '确认提交'}
            </Button>
          </>
        }
      >
        <Notice tone="warn">提交后将进入柜台报单流程；模拟环境不会触达真实柜台。</Notice>
        <KeyValue
          items={[
            { label: '账户', value: accountId || '-' },
            { label: '合约', value: symbol.trim() || '-' },
            { label: '方向', value: directionLabel(direction) },
            { label: '开平', value: offsetFlag },
            { label: '类型', value: orderType },
            { label: '价格', value: orderType === 'MARKET' && !price.trim() ? '市价' : fmt(toNum(price)) },
            { label: '数量', value: fmt(toNum(quantity)) },
            { label: '名义价值', value: notional === undefined ? '-' : `¥${fmt(notional)}` },
          ]}
        />
      </Modal>
    </Page>
  );
}

function OrderFields({ order }: { order: OrderView }) {
  return (
    <KeyValue
      items={[
        { label: 'orderId', value: order.orderId },
        {
          label: 'status',
          value: <Badge tone={statusToneOf(order.status)}>{order.status ?? '-'}</Badge>,
        },
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
