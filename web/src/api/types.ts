export interface Result<T> {
  code: number;
  message: string;
  data: T;
  timestamp: number;
}

export interface AuthState {
  role: string;
  userId: string;
  group: string;
}

export interface FundAccount {
  accountId: string;
  accountName?: string;
  totalAsset: number;
  availableCash: number;
  frozenCash: number;
  margin: number;
  unrealizedPnl: number;
  realizedPnl: number;
}

export interface PlatformPosition {
  id?: number;
  accountId: string;
  strategyId: string;
  symbol: string;
  direction: string;
  quantity: number;
  availableQuantity?: number;
  avgCost: number;
  unrealizedPnl?: number;
}

export interface Order {
  orderId: string;
  accountId?: string;
  strategyId?: string;
  symbol: string;
  direction: string;
  offsetFlag?: string;
  orderType?: string;
  price?: number;
  quantity: number;
  filledQuantity?: number;
  status: string;
  createTime?: string;
}

export interface TradeSubmissionResult {
  orderId: string;
  status: string;
  brokerOrderRef?: string;
  gatewayId?: string;
  idempotent?: boolean;
  message?: string;
  mode?: string;
  filledImmediately?: boolean;
}

export interface AlertEvent {
  eventId: string;
  ruleId?: string;
  metricKey: string;
  value: string;
  severity: string;
  message?: string;
  status: string;
  ackBy?: string;
  ackTime?: string;
  createTime?: string;
}

export interface AbnormalAlert {
  alertId: string;
  ruleCode: string;
  accountId?: string;
  strategyId?: string;
  symbol?: string;
  score: number;
  detailJson?: string;
  severity: string;
  status: string;
  handler?: string;
  handleNote?: string;
  createTime?: string;
}
