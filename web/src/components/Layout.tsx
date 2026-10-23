import { useEffect, useRef, useState } from 'react';
import { NavLink, Navigate, Outlet, useLocation } from 'react-router-dom';
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
      { to: '/risk-board', label: '风控看板' },
      { to: '/degrade', label: '性能降级' },
    ],
  },
];

function currentCrumb(pathname: string): { group: string; page: string } {
  for (const group of NAV) {
    const match = group.items.find((item) => pathname === item.to);
    if (match) {
      return { group: group.group, page: match.label };
    }
  }
  return { group: '控制台', page: '仪表盘' };
}

export default function Layout() {
  const { session, logout } = useAuth();
  const location = useLocation();
  const [navOpen, setNavOpen] = useState(false);
  const [dark, setDark] = useState(
    () => document.documentElement.dataset.theme === 'dark',
  );
  const menuBtnRef = useRef<HTMLButtonElement>(null);
  const sidebarRef = useRef<HTMLElement>(null);

  useEffect(() => {
    setNavOpen(false);
  }, [location.pathname]);

  useEffect(() => {
    if (!navOpen) {
      return;
    }
    const main = document.querySelector<HTMLElement>('.main');
    main?.setAttribute('inert', '');
    document.body.style.overflow = 'hidden';
    const firstLink = sidebarRef.current?.querySelector<HTMLElement>('a[href]');
    firstLink?.focus();

    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        setNavOpen(false);
      }
    };
    window.addEventListener('keydown', onKey);
    return () => {
      window.removeEventListener('keydown', onKey);
      main?.removeAttribute('inert');
      document.body.style.overflow = '';
      menuBtnRef.current?.focus();
    };
  }, [navOpen]);

  if (!session) {
    return <Navigate to="/login" replace />;
  }

  function toggleTheme() {
    const next = dark ? 'light' : 'dark';
    document.documentElement.dataset.theme = next;
    localStorage.setItem('lhjypt.theme', next);
    setDark(!dark);
  }

  const crumb = currentCrumb(location.pathname);

  return (
    <div className="shell">
      <a className="skip-link" href="#main">
        跳到主内容
      </a>
      {navOpen && (
        <button
          type="button"
          className="scrim-nav"
          aria-label="关闭导航"
          onClick={() => setNavOpen(false)}
        />
      )}
      <aside
        ref={sidebarRef}
        className={`sidebar ${navOpen ? 'is-open' : ''}`}
        aria-label="主导航"
      >
        <div className="brand">
          <span className="brand-mark" aria-hidden="true">
            LH
          </span>
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
                  onClick={() => setNavOpen(false)}
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
          <button
            ref={menuBtnRef}
            type="button"
            className="icon-btn menu-btn"
            aria-label="打开导航"
            aria-expanded={navOpen}
            onClick={() => setNavOpen(true)}
          >
            ☰
          </button>
          <div className="topbar-crumbs" aria-label="面包屑">
            <span className="crumb-group">{crumb.group}</span>
            <span className="crumb-sep" aria-hidden="true">
              /
            </span>
            <span className="crumb-page">{crumb.page}</span>
          </div>
          <div className="topbar-spacer" />
          <span className="env-pill">模拟盘</span>
          <button
            type="button"
            className="btn btn-ghost btn-sm"
            aria-pressed={dark}
            aria-label={dark ? '切换到浅色主题' : '切换到深色主题'}
            onClick={toggleTheme}
          >
            {dark ? '浅色' : '深色'}
          </button>
          <div className="topbar-user">
            <Badge tone="info">{session.role}</Badge>
            <span className="muted">{session.userId}</span>
            <button type="button" className="btn btn-ghost btn-sm" onClick={logout}>
              退出
            </button>
          </div>
        </header>
        <main className="content" id="main" tabIndex={-1}>
          <Outlet />
        </main>
      </div>
    </div>
  );
}
