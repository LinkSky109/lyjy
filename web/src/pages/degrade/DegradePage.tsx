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
  Grid,
  Input,
  KeyValue,
  Notice,
  Page,
  Select,
  Stat,
  Tabs,
  Toolbar,
  statusTone,
  type Column,
} from '../../components/ui';

interface DegradeSwitch {
  id?: number;
  switchKey: string;
  label?: string;
  level?: string;
  operator?: string;
  reason?: string;
  updateTime?: string;
}

interface CapacityMap {
  orderTpsCapacity?: number;
  orderTokensAvailable?: number;
  activeSwitches?: string[];
  breakers?: Record<string, string>;
  [key: string]: unknown;
}

const LEVELS = ['OFF', 'ON'];

export default function DegradePage() {
  const [tab, setTab] = useState('switches');
  const [busy, setBusy] = useState(false);
  const [actionError, setActionError] = useState<string | null>(null);
  const [okMsg, setOkMsg] = useState<string | null>(null);

  const [switchKey, setSwitchKey] = useState('');
  const [level, setLevel] = useState('ON');
  const [operator, setOperator] = useState('admin');
  const [reason, setReason] = useState('');

  const [marketLevel, setMarketLevel] = useState('ON');

  const switches = useApi(() => api.get<DegradeSwitch[]>('/api/degrade/switch/list'), []);
  const status = useApi(() => api.get<CapacityMap>('/api/degrade/status'), []);
  const capacity = useApi(() => api.get<CapacityMap>('/api/degrade/capacity'), []);

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

  const setSwitch = () =>
    run(async () => {
      const key = switchKey.trim();
      if (!key) {
        throw new Error('请选择或填写开关 key');
      }
      const { token } = await api.confirmToken();
      const result = await api.post<DegradeSwitch>('/api/degrade/switch', undefined, {
        token,
        params: { key, level, operator: operator.trim() || 'admin', reason: reason.trim() || undefined },
      });
      setOkMsg(`开关 ${result.switchKey} 已设置为 ${result.level}`);
      switches.reload();
      status.reload();
      capacity.reload();
    });

  const backupMarket = () =>
    run(async () => {
      const { token } = await api.confirmToken();
      const result = await api.post<DegradeSwitch>('/api/degrade/backup-market', undefined, {
        token,
        params: { level: marketLevel, operator: operator.trim() || 'admin' },
      });
      setOkMsg(`行情源降级已切换为 ${result.level}`);
      switches.reload();
      status.reload();
      capacity.reload();
    });

  const switchColumns: Column[] = [
    { key: 'switchKey', title: '开关 Key' },
    { key: 'label', title: '说明' },
    {
      key: 'level',
      title: '状态',
      render: (row) => <Badge tone={row.level === 'ON' ? 'warn' : 'ok'}>{row.level ?? '-'}</Badge>,
    },
    { key: 'operator', title: '最近操作人' },
    { key: 'reason', title: '原因' },
    { key: 'updateTime', title: '更新时间' },
  ];

  const breakerColumns: Column[] = [
    { key: 'name', title: '熔断器' },
    {
      key: 'state',
      title: '状态',
      render: (row) => <Badge tone={statusTone(row.state)}>{row.state}</Badge>,
    },
  ];

  function renderCapacity(map: CapacityMap | null, activeList?: string[]) {
    const breakers = Object.entries(map?.breakers ?? {}).map(([name, state]) => ({ name, state }));
    const active = activeList ?? map?.activeSwitches ?? [];
    return (
      <>
        <Grid columns={3}>
          <Stat label="订单 TPS 容量" value={map?.orderTpsCapacity ?? '-'} tone="info" />
          <Stat label="可用令牌数" value={map?.orderTokensAvailable ?? '-'} tone="ok" />
          <Stat label="已启用降级开关" value={active.length} tone={active.length > 0 ? 'warn' : 'ok'} />
        </Grid>

        <h3 style={{ margin: '16px 0 8px', fontSize: 14 }}>已启用开关</h3>
        {active.length === 0 ? (
          <div className="empty">当前无启用的降级开关</div>
        ) : (
          <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
            {active.map((key) => (
              <Badge key={key} tone="warn">
                {key}
              </Badge>
            ))}
          </div>
        )}

        <h3 style={{ margin: '16px 0 8px', fontSize: 14 }}>熔断器状态</h3>
        <DataTable columns={breakerColumns} rows={breakers} empty="无熔断器" rowKey={(row) => row.name} />
      </>
    );
  }

  return (
    <Page title="性能降级预案" subtitle="非核心功能降级开关、熔断状态与容量水位">
      <Tabs
        tabs={[
          { key: 'switches', label: '开关' },
          { key: 'status', label: '状态' },
          { key: 'capacity', label: '容量' },
        ]}
        active={tab}
        onChange={setTab}
      />

      {actionError && <Notice tone="danger">{actionError}</Notice>}
      {okMsg && <Notice tone="ok">{okMsg}</Notice>}

      {tab === 'switches' && (
        <>
          <Card title="设置降级开关">
            <Notice tone="warn">
              敏感操作：核心链路（degrade.core.*）永不降级，开关变更需一次性确认令牌并写入告警留痕。
            </Notice>
            <Toolbar>
              <Field label="开关 Key">
                <Select value={switchKey} onChange={(event) => setSwitchKey(event.target.value)}>
                  <option value="">请选择</option>
                  {(switches.data ?? []).map((item) => (
                    <option key={item.switchKey} value={item.switchKey}>
                      {item.switchKey}（{item.label ?? '-'}）
                    </option>
                  ))}
                </Select>
              </Field>
              <Field label="级别（level）">
                <Select value={level} onChange={(event) => setLevel(event.target.value)}>
                  {LEVELS.map((item) => (
                    <option key={item} value={item}>
                      {item}
                    </option>
                  ))}
                </Select>
              </Field>
              <Field label="操作人（operator）">
                <Input value={operator} onChange={(event) => setOperator(event.target.value)} />
              </Field>
              <Field label="原因（reason）">
                <Input value={reason} onChange={(event) => setReason(event.target.value)} />
              </Field>
              <Button variant="danger" disabled={busy || !switchKey} onClick={setSwitch}>
                设置开关
              </Button>
            </Toolbar>

            <Toolbar>
              <Field label="行情源降级（degrade.market.backup）">
                <Select value={marketLevel} onChange={(event) => setMarketLevel(event.target.value)}>
                  {LEVELS.map((item) => (
                    <option key={item} value={item}>
                      {item}
                    </option>
                  ))}
                </Select>
              </Field>
              <Button variant="primary" disabled={busy} onClick={backupMarket}>
                切换行情源
              </Button>
            </Toolbar>
          </Card>

          <Card title="开关列表" actions={<Button size="sm" disabled={busy} onClick={() => switches.reload()}>刷新</Button>}>
            <AsyncState
              loading={switches.loading}
              error={switches.error}
              onRetry={switches.reload}
              empty={!switches.data || switches.data.length === 0 ? '暂无降级开关' : false}
            >
              <DataTable columns={switchColumns} rows={switches.data} rowKey={(row) => row.switchKey} />
            </AsyncState>
          </Card>
        </>
      )}

      {tab === 'status' && (
        <Card title="降级状态与熔断器" actions={<Button size="sm" disabled={busy} onClick={() => status.reload()}>刷新</Button>}>
          <AsyncState loading={status.loading} error={status.error} onRetry={status.reload}>
            {renderCapacity(status.data)}
          </AsyncState>
        </Card>
      )}

      {tab === 'capacity' && (
        <Card title="容量水位" actions={<Button size="sm" disabled={busy} onClick={() => capacity.reload()}>刷新</Button>}>
          <AsyncState loading={capacity.loading} error={capacity.error} onRetry={capacity.reload}>
            {renderCapacity(capacity.data)}
            <h3 style={{ margin: '16px 0 8px', fontSize: 14 }}>完整水位</h3>
            <KeyValue
              items={Object.entries(capacity.data ?? {}).map(([key, value]) => ({
                label: key,
                value: typeof value === 'object' ? JSON.stringify(value) : String(value),
              }))}
            />
          </AsyncState>
        </Card>
      )}
    </Page>
  );
}
