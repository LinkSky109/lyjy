import { api } from '../../api/http';
import { useApi } from '../../hooks/useApi';
import {
  AsyncState,
  Badge,
  Card,
  DataTable,
  Grid,
  Page,
  Stat,
  statusTone,
} from '../../components/ui';

interface Health {
  status: string;
  components?: Record<string, { status: string }>;
}

export default function DashboardPage() {
  const health = useApi(() => api.get<Health>('/actuator/health'), []);
  const metrics = useApi(() => api.get<Record<string, unknown>>('/api/monitor/metrics/latest'), []);
  const alerts = useApi(() => api.get<any[]>('/api/monitor/alerts', { status: 'OPEN' }), []);

  const dbStatus = health.data?.components?.db?.status ?? '-';
  const openAlerts = alerts.data ?? [];
  const metricEntries = Object.entries(metrics.data ?? {});

  return (
    <Page title="仪表盘" subtitle="系统健康、实时指标与未闭环告警总览">
      <Grid columns={4}>
        <Stat
          label="服务健康"
          value={health.data?.status ?? '-'}
          tone={health.data?.status === 'UP' ? 'ok' : 'danger'}
          hint={health.error ?? undefined}
        />
        <Stat label="数据源" value={dbStatus} tone={dbStatus === 'UP' ? 'ok' : 'warn'} />
        <Stat
          label="未闭环告警"
          value={openAlerts.length}
          tone={openAlerts.length > 0 ? 'warn' : 'ok'}
        />
        <Stat label="指标项" value={metricEntries.length} tone="info" />
      </Grid>

      <Card title="最新指标快照">
        <AsyncState loading={metrics.loading} error={metrics.error} onRetry={metrics.reload} empty={metricEntries.length === 0 && '暂无指标'}>
          <DataTable
            columns={[
              { key: 'metric', title: '指标', render: (row) => row[0] },
              { key: 'value', title: '值', render: (row) => String(row[1]) },
            ]}
            rows={metricEntries}
            rowKey={(row) => String(row[0])}
          />
        </AsyncState>
      </Card>

      <Card title="未闭环告警">
        <AsyncState loading={alerts.loading} error={alerts.error} onRetry={alerts.reload} empty={openAlerts.length === 0 && '暂无未闭环告警'}>
          <DataTable
            columns={[
              { key: 'eventId', title: '事件号' },
              { key: 'metricKey', title: '指标' },
              { key: 'value', title: '值' },
              {
                key: 'severity',
                title: '级别',
                render: (row) => <Badge tone={statusTone(row.severity)}>{row.severity}</Badge>,
              },
              {
                key: 'status',
                title: '状态',
                render: (row) => <Badge tone={statusTone(row.status)}>{row.status}</Badge>,
              },
              { key: 'message', title: '说明' },
            ]}
            rows={openAlerts}
            rowKey={(row) => row.eventId}
          />
        </AsyncState>
      </Card>
    </Page>
  );
}
