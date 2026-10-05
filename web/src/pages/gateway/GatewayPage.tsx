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

interface GatewaySummary {
  gatewayId: string;
  type?: string;
  name?: string;
  priority?: number;
  status?: string;
  connected?: boolean;
  authenticated?: boolean;
  breaker?: string;
  failureRate?: number;
  lastHealthAt?: string;
  lastError?: string;
  orderListenerCount?: number;
  tradeListenerCount?: number;
}

interface GatewayInfo {
  gatewayId: string;
  gatewayType: string;
  name?: string;
  status?: string;
  paramConfig?: string;
  priority?: number;
  healthPath?: string;
  description?: string;
}

interface GatewayRouteRule {
  id?: number;
  ruleId?: string;
  ruleName?: string;
  accountId?: string;
  symbolRegex?: string;
  orderType?: string;
  gatewayId?: string;
  fallbackId?: string;
  priority?: number;
  enabled?: boolean;
}

const GATEWAY_TYPES = ['MOCK', 'CTP', 'FIX'];
const ORDER_TYPES = ['', 'LIMIT', 'MARKET'];

export default function GatewayPage() {
  const [tab, setTab] = useState('gateways');
  const [busy, setBusy] = useState(false);
  const [actionError, setActionError] = useState<string | null>(null);
  const [okMsg, setOkMsg] = useState<string | null>(null);

  const [selectedId, setSelectedId] = useState('');

  // 注册表单
  const [gatewayId, setGatewayId] = useState('');
  const [gatewayType, setGatewayType] = useState('MOCK');
  const [gwName, setGwName] = useState('');
  const [priority, setPriority] = useState('100');
  const [paramConfig, setParamConfig] = useState('{}');
  const [description, setDescription] = useState('');

  // 连接 / 健康
  const [connectTarget, setConnectTarget] = useState('');
  const [connectJson, setConnectJson] = useState('');
  const [health, setHealth] = useState<Record<string, unknown> | null>(null);

  // 手动切换
  const [switchTarget, setSwitchTarget] = useState('');
  const [switchAccountId, setSwitchAccountId] = useState('');
  const [switchFallbackId, setSwitchFallbackId] = useState('');
  const [switchOperator, setSwitchOperator] = useState('admin');

  // 路由表单
  const [ruleId, setRuleId] = useState('');
  const [ruleName, setRuleName] = useState('');
  const [ruleAccountId, setRuleAccountId] = useState('');
  const [symbolRegex, setSymbolRegex] = useState('');
  const [orderType, setOrderType] = useState('');
  const [ruleGatewayId, setRuleGatewayId] = useState('');
  const [ruleFallbackId, setRuleFallbackId] = useState('');
  const [rulePriority, setRulePriority] = useState('100');
  const [ruleEnabled, setRuleEnabled] = useState('true');

  const gateways = useApi(() => api.get<GatewaySummary[]>('/api/gateway/list'), []);
  const routes = useApi(() => api.get<GatewayRouteRule[]>('/api/gateway/route/list'), []);

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

  const registerGateway = () =>
    run(async () => {
      if (!gatewayId.trim()) {
        throw new Error('gatewayId 不能为空');
      }
      const body: GatewayInfo = {
        gatewayId: gatewayId.trim(),
        gatewayType,
        name: gwName.trim() || undefined,
        priority: Number(priority) || undefined,
        paramConfig: paramConfig.trim() || '{}',
        description: description.trim() || undefined,
      };
      await api.post<GatewayInfo>('/api/gateway/register', body);
      setOkMsg(`网关 ${body.gatewayId} 已注册`);
      setGatewayId('');
      setGwName('');
      gateways.reload();
    });

  const submitConnect = () =>
    run(async () => {
      if (!connectTarget) {
        return;
      }
      const config: unknown = connectJson.trim() ? JSON.parse(connectJson) : undefined;
      await api.post<void>(`/api/gateway/${connectTarget}/connect`, config);
      setOkMsg(`网关 ${connectTarget} 连接指令已下发`);
      setConnectTarget('');
      setConnectJson('');
      gateways.reload();
    });

  const disconnect = (id: string) =>
    run(async () => {
      await api.post<void>(`/api/gateway/${id}/disconnect`);
      setOkMsg(`网关 ${id} 已断开`);
      gateways.reload();
    });

  const loadHealth = (id: string) =>
    run(async () => {
      const result = await api.get<Record<string, unknown>>(`/api/gateway/${id}/health`);
      setHealth(result);
      setSelectedId(id);
    });

  const submitSwitch = () =>
    run(async () => {
      if (!switchTarget) {
        return;
      }
      if (!switchAccountId.trim()) {
        throw new Error('accountId 不能为空');
      }
      const { token } = await api.confirmToken();
      await api.post<GatewayRouteRule>(
        `/api/gateway/${switchTarget}/switch`,
        undefined,
        {
          token,
          params: {
            accountId: switchAccountId.trim(),
            fallbackId: switchFallbackId.trim() || undefined,
            operator: switchOperator.trim() || 'admin',
          },
        },
      );
      setOkMsg(`账户 ${switchAccountId} 已切换至网关 ${switchTarget}`);
      setSwitchTarget('');
      setSwitchAccountId('');
      setSwitchFallbackId('');
      routes.reload();
    });

  const upsertRoute = () =>
    run(async () => {
      if (!ruleGatewayId.trim()) {
        throw new Error('主网关 gatewayId 不能为空');
      }
      const body: GatewayRouteRule = {
        ruleId: ruleId.trim() || undefined,
        ruleName: ruleName.trim() || undefined,
        accountId: ruleAccountId.trim() || undefined,
        symbolRegex: symbolRegex.trim() || undefined,
        orderType: orderType || undefined,
        gatewayId: ruleGatewayId.trim(),
        fallbackId: ruleFallbackId.trim() || undefined,
        priority: Number(rulePriority) || undefined,
        enabled: ruleEnabled === 'true',
      };
      const saved = await api.post<GatewayRouteRule>('/api/gateway/route', body);
      setOkMsg(`路由规则 ${saved.ruleId ?? ''} 已保存`);
      routes.reload();
    });

  const gatewayColumns: Column[] = [
    { key: 'gatewayId', title: '网关 ID' },
    { key: 'type', title: '类型' },
    { key: 'name', title: '名称' },
    { key: 'priority', title: '优先级', align: 'right' },
    {
      key: 'status',
      title: '状态',
      render: (row) => <Badge tone={statusTone(row.status)}>{row.status ?? '-'}</Badge>,
    },
    { key: 'connected', title: '已连接' },
    { key: 'authenticated', title: '已认证' },
    {
      key: 'breaker',
      title: '熔断器',
      render: (row) => <Badge tone={statusTone(row.breaker)}>{row.breaker ?? '-'}</Badge>,
    },
    { key: 'failureRate', title: '失败率', align: 'right' },
    { key: 'lastError', title: '最近错误' },
    {
      key: 'actions',
      title: '操作',
      render: (row) => (
        <div className="page-actions">
          <Button size="sm" disabled={busy} onClick={() => { setConnectTarget(row.gatewayId); setConnectJson(''); }}>
            连接
          </Button>
          <Button size="sm" disabled={busy} onClick={() => disconnect(row.gatewayId)}>
            断开
          </Button>
          <Button size="sm" disabled={busy} onClick={() => loadHealth(row.gatewayId)}>
            健康
          </Button>
          <Button size="sm" disabled={busy} onClick={() => setSwitchTarget(row.gatewayId)}>
            切换
          </Button>
        </div>
      ),
    },
  ];

  const routeColumns: Column[] = [
    { key: 'ruleId', title: '规则 ID' },
    { key: 'ruleName', title: '规则名' },
    { key: 'accountId', title: '账户' },
    { key: 'symbolRegex', title: '标的正则' },
    { key: 'orderType', title: '订单类型' },
    { key: 'gatewayId', title: '主网关' },
    { key: 'fallbackId', title: '备用网关' },
    { key: 'priority', title: '优先级', align: 'right' },
    {
      key: 'enabled',
      title: '启用',
      render: (row) => <Badge tone={row.enabled ? 'ok' : 'muted'}>{row.enabled ? '是' : '否'}</Badge>,
    },
  ];

  return (
    <Page title="多网关与路由治理" subtitle="网关注册连接、健康检查、路由规则与账户手动切换">
      <Tabs
        tabs={[
          { key: 'gateways', label: '网关' },
          { key: 'routes', label: '路由' },
        ]}
        active={tab}
        onChange={setTab}
      />

      {actionError && <Notice tone="danger">{actionError}</Notice>}
      {okMsg && <Notice tone="ok">{okMsg}</Notice>}

      {tab === 'gateways' && (
        <>
          <Card title="注册网关">
            <Toolbar>
              <Field label="网关 ID（gatewayId）">
                <Input value={gatewayId} onChange={(event) => setGatewayId(event.target.value)} placeholder="GW-MOCK-01" />
              </Field>
              <Field label="类型（gatewayType）">
                <Select value={gatewayType} onChange={(event) => setGatewayType(event.target.value)}>
                  {GATEWAY_TYPES.map((item) => (
                    <option key={item} value={item}>
                      {item}
                    </option>
                  ))}
                </Select>
              </Field>
              <Field label="名称">
                <Input value={gwName} onChange={(event) => setGwName(event.target.value)} placeholder="模拟主通道" />
              </Field>
              <Field label="优先级">
                <Input value={priority} onChange={(event) => setPriority(event.target.value)} placeholder="100" />
              </Field>
            </Toolbar>
            <Field label="参数配置（paramConfig，JSON）" hint="生产环境只保存密钥引用，切勿放入明文口令">
              <Textarea value={paramConfig} onChange={(event) => setParamConfig(event.target.value)} />
            </Field>
            <Field label="描述">
              <Input value={description} onChange={(event) => setDescription(event.target.value)} />
            </Field>
            <Button variant="primary" disabled={busy} onClick={registerGateway}>
              注册 / 更新网关
            </Button>
          </Card>

          <Card title="网关列表" actions={<Button size="sm" disabled={busy} onClick={() => gateways.reload()}>刷新</Button>}>
            <AsyncState
              loading={gateways.loading}
              error={gateways.error}
              onRetry={gateways.reload}
              empty={!gateways.data || gateways.data.length === 0 ? '暂无已注册网关' : false}
            >
              <DataTable columns={gatewayColumns} rows={gateways.data} rowKey={(row) => row.gatewayId} />
            </AsyncState>
          </Card>

          {health && (
            <Card title={`健康详情${selectedId ? ` · ${selectedId}` : ''}`} actions={<Button size="sm" onClick={() => setHealth(null)}>关闭</Button>}>
              <JsonBlock value={health} />
            </Card>
          )}

          <Card title="手动切换账户流量">
            <Notice tone="warn">敏感操作：切换为账户指定新网关/备用网关，需一次性确认令牌。</Notice>
            <Toolbar>
              <Field label="目标网关（gatewayId）">
                <Input value={switchTarget} onChange={(event) => setSwitchTarget(event.target.value)} placeholder="GW-MOCK-01" />
              </Field>
              <Field label="账户 ID（accountId）">
                <Input value={switchAccountId} onChange={(event) => setSwitchAccountId(event.target.value)} placeholder="DEFAULT-001" />
              </Field>
              <Field label="备用网关（fallbackId，可选）">
                <Input value={switchFallbackId} onChange={(event) => setSwitchFallbackId(event.target.value)} />
              </Field>
              <Field label="操作人（operator）">
                <Input value={switchOperator} onChange={(event) => setSwitchOperator(event.target.value)} />
              </Field>
              <Button variant="danger" disabled={busy || !switchTarget} onClick={submitSwitch}>
                确认切换
              </Button>
            </Toolbar>
          </Card>
        </>
      )}

      {tab === 'routes' && (
        <>
          <Card title="新建 / 更新路由规则">
            <Toolbar>
              <Field label="规则 ID（ruleId）" hint="留空则新建">
                <Input value={ruleId} onChange={(event) => setRuleId(event.target.value)} />
              </Field>
              <Field label="规则名">
                <Input value={ruleName} onChange={(event) => setRuleName(event.target.value)} />
              </Field>
              <Field label="账户（accountId）">
                <Input value={ruleAccountId} onChange={(event) => setRuleAccountId(event.target.value)} />
              </Field>
              <Field label="标的正则（symbolRegex）">
                <Input value={symbolRegex} onChange={(event) => setSymbolRegex(event.target.value)} placeholder="rb.*" />
              </Field>
              <Field label="订单类型">
                <Select value={orderType} onChange={(event) => setOrderType(event.target.value)}>
                  {ORDER_TYPES.map((item) => (
                    <option key={item || 'ALL'} value={item}>
                      {item || '不限'}
                    </option>
                  ))}
                </Select>
              </Field>
              <Field label="主网关（gatewayId）">
                <Input value={ruleGatewayId} onChange={(event) => setRuleGatewayId(event.target.value)} />
              </Field>
              <Field label="备用网关（fallbackId）">
                <Input value={ruleFallbackId} onChange={(event) => setRuleFallbackId(event.target.value)} />
              </Field>
              <Field label="优先级">
                <Input value={rulePriority} onChange={(event) => setRulePriority(event.target.value)} />
              </Field>
              <Field label="启用">
                <Select value={ruleEnabled} onChange={(event) => setRuleEnabled(event.target.value)}>
                  <option value="true">是</option>
                  <option value="false">否</option>
                </Select>
              </Field>
              <Button variant="primary" disabled={busy} onClick={upsertRoute}>
                保存规则
              </Button>
            </Toolbar>
          </Card>

          <Card title="路由规则列表" actions={<Button size="sm" disabled={busy} onClick={() => routes.reload()}>刷新</Button>}>
            <AsyncState
              loading={routes.loading}
              error={routes.error}
              onRetry={routes.reload}
              empty={!routes.data || routes.data.length === 0 ? '暂无路由规则' : false}
            >
              <DataTable columns={routeColumns} rows={routes.data} rowKey={(row, index) => row.ruleId ?? String(index)} />
            </AsyncState>
          </Card>
        </>
      )}

      <Modal
        open={connectTarget !== ''}
        title={`连接网关${connectTarget ? ` · ${connectTarget}` : ''}`}
        onClose={() => setConnectTarget('')}
        footer={
          <>
            <Button onClick={() => setConnectTarget('')} disabled={busy}>
              取消
            </Button>
            <Button variant="primary" onClick={submitConnect} disabled={busy}>
              下发连接
            </Button>
          </>
        }
      >
        <Notice tone="info">连接体为可选 CTPConfig JSON；留空则使用网关已保存的 paramConfig。</Notice>
        <Field label="CTPConfig（JSON，可选）" hint="包含 password 等凭据，请勿在共享环境明文留存">
          <Textarea value={connectJson} onChange={(event) => setConnectJson(event.target.value)} placeholder='{"mode":"MOCK"}' />
        </Field>
      </Modal>
    </Page>
  );
}
