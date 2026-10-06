import { NavLink, Navigate, Outlet } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { Badge } from './ui';

const NAV: Array<{ group: string; items: Array<{ to: string; label: string }> }> = [
  { group: '总览', items: [{ to: '/dashboard', label: '仪表盘' }] },
  {
    group: '交易执行',
    items: [
      { to: '/trading', label: '交易下单' },
      { to: '/fund', label: '资金管理' },
    ],
  },
  {
    group: '风险控制',
    items: [
      { to: '/risk', label: '事前风控' },
      { to: '/intraday', label: '事中风控' },
      { to: '/var', label: 'VaR 风险价值' },
      { to: '/surveillance', label: '异常交易' },
    ],
  },
  {
    group: '资产与清算',
    items: [
      { to: '/portfolio', label: '组合管理' },
      { to: '/settlement', label: '日终清算' },
    ],
  },
  {
    group: '平台治理',
    items: [
      { to: '/gateway', label: '多柜台网关' },
      { to: '/monitor', label: '监控告警' },
      { to: '/strategy', label: '策略安全' },
      { to: '/backtest', label: '回测' },
      { to: '/degrade', label: '性能降级' },
    ],
  },
];

export default function Layout() {
  const { session, logout } = useAuth();
  if (!session) {
    return <Navigate to="/login" replace />;
  }
  return (
    <div className="shell">
      <aside className="sidebar">
        <div className="brand">
          <span className="brand-mark">LH</span>
          <div>
            <strong>lhjypt</strong>
            <small>量化交易管理平台</small>
          </div>
        </div>
        <nav className="nav">
          {NAV.map((group) => (
            <div className="nav-group" key={group.group}>
              <span className="nav-group-label">{group.group}</span>
              {group.items.map((item) => (
                <NavLink
                  key={item.to}
                  to={item.to}
                  className={({ isActive }) => `nav-item ${isActive ? 'active' : ''}`}
                >
                  {item.label}
                </NavLink>
              ))}
            </div>
          ))}
        </nav>
        <div className="sidebar-foot">v0.1.0 · P1</div>
      </aside>
      <div className="main">
        <header className="topbar">
          <div className="topbar-title">企业管理控制台</div>
          <div className="topbar-user">
            <Badge tone="info">{session.role}</Badge>
            <span className="muted">{session.userId}</span>
            <button type="button" className="btn btn-ghost btn-sm" onClick={logout}>
              退出
            </button>
          </div>
        </header>
        <main className="content">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
