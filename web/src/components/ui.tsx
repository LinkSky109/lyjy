import {
  useEffect,
  useId,
  useRef,
  type InputHTMLAttributes,
  type KeyboardEvent as ReactKeyboardEvent,
  type ReactNode,
  type SelectHTMLAttributes,
  type TextareaHTMLAttributes,
} from 'react';

export type Tone = 'ok' | 'warn' | 'danger' | 'info' | 'muted';

export function Page({
  title,
  subtitle,
  actions,
  children,
}: {
  title: string;
  subtitle?: string;
  actions?: ReactNode;
  children: ReactNode;
}) {
  return (
    <div className="page">
      <div className="page-head">
        <div>
          <h1>{title}</h1>
          {subtitle && <p className="muted">{subtitle}</p>}
        </div>
        {actions && <div className="page-actions">{actions}</div>}
      </div>
      {children}
    </div>
  );
}

export function Card({
  title,
  actions,
  children,
  padded = true,
}: {
  title?: string;
  actions?: ReactNode;
  children: ReactNode;
  padded?: boolean;
}) {
  return (
    <section className="card">
      {(title || actions) && (
        <header className="card-head">
          {title && <h2>{title}</h2>}
          {actions && <div className="card-actions">{actions}</div>}
        </header>
      )}
      <div className={padded ? 'card-body' : ''}>{children}</div>
    </section>
  );
}

export function Toolbar({ children }: { children: ReactNode }) {
  return <div className="toolbar">{children}</div>;
}

export function Button({
  children,
  onClick,
  variant = 'default',
  size = 'md',
  disabled,
  type = 'button',
  title,
}: {
  children: ReactNode;
  onClick?: () => void;
  variant?: 'default' | 'primary' | 'danger' | 'ghost';
  size?: 'sm' | 'md';
  disabled?: boolean;
  type?: 'button' | 'submit';
  title?: string;
}) {
  return (
    <button
      type={type}
      title={title}
      className={`btn btn-${variant} btn-${size}`}
      onClick={onClick}
      disabled={disabled}
    >
      {children}
    </button>
  );
}

export function Badge({ tone = 'muted', children }: { tone?: Tone; children: ReactNode }) {
  return <span className={`badge badge-${tone}`}>{children}</span>;
}

export function statusTone(value: string | undefined | null): Tone {
  const text = (value ?? '').toUpperCase();
  if (
    [
      'UP',
      'READY',
      'ACTIVE',
      'COMPLETED',
      'CLOSED',
      'SUCCESS',
      'FILLED',
      'PASSED',
      'CONFIRMED',
      'ON',
      'RUNNING',
      'HEALTHY',
      'CONNECTED',
      'MATCHED',
    ].includes(text)
  ) {
    return 'ok';
  }
  if (
    [
      'WARN',
      'REVIEWING',
      'PENDING',
      'PARTIALLY_COMPLETED',
      'ADJUSTING',
      'OPEN',
      'ACKED',
      'DEGRADED',
      'PARTIAL_FILLED',
      'PARTIALLY_FILLED',
      'CONNECTING',
      'PAUSED',
      'OFFLINE',
      'STOPPED',
      'INFO',
      'UNKNOWN',
    ].includes(text)
  ) {
    return 'warn';
  }
  if (
    [
      'CRITICAL',
      'FAILED',
      'DOWN',
      'ERROR',
      'REJECTED',
      'CANCELED',
      'CANCELLED',
      'TIMEOUT',
      'MISMATCHED',
      'BLOCK',
    ].includes(text)
  ) {
    return 'danger';
  }
  return 'muted';
}

export function Field({
  label,
  hint,
  error,
  children,
}: {
  label: string;
  hint?: string;
  error?: string;
  children: ReactNode;
}) {
  return (
    <label className="field">
      <span className="field-label">{label}</span>
      {children}
      {error ? (
        <span className="field-error" role="alert">
          {error}
        </span>
      ) : (
        hint && <span className="field-hint">{hint}</span>
      )}
    </label>
  );
}

export function Input(props: InputHTMLAttributes<HTMLInputElement>) {
  return <input {...props} className={`input ${props.className ?? ''}`} />;
}

export function Select(props: SelectHTMLAttributes<HTMLSelectElement>) {
  return <select {...props} className={`input ${props.className ?? ''}`} />;
}

export function Textarea(props: TextareaHTMLAttributes<HTMLTextAreaElement>) {
  return <textarea {...props} className={`input ${props.className ?? ''}`} />;
}

export interface Column {
  key: string;
  title: string;
  render?: (row: any) => ReactNode;
  width?: string;
  align?: 'left' | 'right' | 'center';
}

export function DataTable({
  columns,
  rows,
  rowKey,
  empty = '暂无数据',
  loading,
}: {
  columns: Column[];
  rows: any[] | null | undefined;
  rowKey?: (row: any, index: number) => string;
  empty?: string;
  loading?: boolean;
}) {
  if (loading) {
    return (
      <div className="skeleton" aria-busy="true" aria-label="加载中">
        <span className="skeleton-line" style={{ width: '38%' }} />
        <span className="skeleton-line" />
        <span className="skeleton-line" />
        <span className="skeleton-line" style={{ width: '72%' }} />
      </div>
    );
  }
  if (!rows || rows.length === 0) {
    return <div className="empty">{empty}</div>;
  }
  return (
    <div className="table-wrap">
      <table className="table">
        <thead>
          <tr>
            {columns.map((column) => (
              <th
                key={column.key}
                scope="col"
                className={column.align === 'right' ? 'num' : undefined}
                style={{ width: column.width }}
              >
                {column.title}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((row, index) => (
            <tr key={rowKey ? rowKey(row, index) : index}>
              {columns.map((column) => (
                <td
                  key={column.key}
                  data-label={column.title}
                  className={column.align === 'right' ? 'num' : undefined}
                >
                  {column.render ? column.render(row) : formatCell(row[column.key])}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function formatCell(value: unknown): ReactNode {
  if (value === null || value === undefined || value === '') {
    return <span className="muted">-</span>;
  }
  if (typeof value === 'boolean') {
    return value ? '是' : '否';
  }
  if (typeof value === 'object') {
    return <code className="inline-code">{JSON.stringify(value)}</code>;
  }
  return String(value);
}

export function AsyncState({
  loading,
  error,
  onRetry,
  empty,
  children,
}: {
  loading?: boolean;
  error?: string | null;
  onRetry?: () => void;
  empty?: string | false;
  children?: ReactNode;
}) {
  if (loading) {
    return (
      <div className="skeleton" aria-busy="true" aria-label="加载中">
        <span className="skeleton-line" style={{ width: '42%' }} />
        <span className="skeleton-line" />
        <span className="skeleton-line" style={{ width: '68%' }} />
      </div>
    );
  }
  if (error) {
    return (
      <div className="error-box" role="alert">
        <span>{error}</span>
        {onRetry && (
          <Button size="sm" onClick={onRetry}>
            重试
          </Button>
        )}
      </div>
    );
  }
  if (empty) {
    return <div className="empty">{empty}</div>;
  }
  return <>{children}</>;
}

export function Stat({
  label,
  value,
  tone = 'muted',
  hint,
}: {
  label: string;
  value: ReactNode;
  tone?: Tone;
  hint?: string;
}) {
  return (
    <div className={`stat stat-${tone}`}>
      <span className="stat-label">{label}</span>
      <span className="stat-value">{value}</span>
      {hint && <span className="stat-hint">{hint}</span>}
    </div>
  );
}

export function KeyValue({ items }: { items: Array<{ label: string; value: ReactNode }> }) {
  return (
    <dl className="kv">
      {items.map((item) => (
        <div className="kv-row" key={item.label}>
          <dt>{item.label}</dt>
          <dd>{item.value}</dd>
        </div>
      ))}
    </dl>
  );
}

export function JsonBlock({ value }: { value: unknown }) {
  const text = typeof value === 'string' ? pretty(value) : JSON.stringify(value, null, 2);
  return <pre className="json-block">{text}</pre>;
}

function pretty(value: string): string {
  try {
    return JSON.stringify(JSON.parse(value), null, 2);
  } catch {
    return value;
  }
}

const FOCUSABLE =
  'a[href], button:not([disabled]), textarea, input, select, [tabindex]:not([tabindex="-1"])';

export function Modal({
  open,
  title,
  onClose,
  children,
  footer,
  wide,
}: {
  open: boolean;
  title: string;
  onClose: () => void;
  children: ReactNode;
  footer?: ReactNode;
  wide?: boolean;
}) {
  const titleId = useId();
  const dialogRef = useRef<HTMLDivElement>(null);
  const lastFocus = useRef<HTMLElement | null>(null);
  const onCloseRef = useRef(onClose);
  onCloseRef.current = onClose;

  useEffect(() => {
    if (!open) {
      return;
    }
    lastFocus.current = document.activeElement as HTMLElement | null;
    const dialog = dialogRef.current;
    if (!dialog) {
      return;
    }
    const nodes = Array.from(dialog.querySelectorAll<HTMLElement>(FOCUSABLE));
    const primary = nodes[nodes.length - 1];
    (primary ?? dialog).focus();

    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        event.preventDefault();
        onCloseRef.current();
        return;
      }
      if (event.key !== 'Tab') {
        return;
      }
      const items = Array.from(dialog.querySelectorAll<HTMLElement>(FOCUSABLE));
      if (items.length === 0) {
        event.preventDefault();
        return;
      }
      const first = items[0];
      const last = items[items.length - 1];
      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault();
        last.focus();
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault();
        first.focus();
      }
    };

    window.addEventListener('keydown', onKey);
    return () => {
      window.removeEventListener('keydown', onKey);
      lastFocus.current?.focus();
    };
  }, [open]);

  if (!open) {
    return null;
  }

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div
        ref={dialogRef}
        className={`modal ${wide ? 'modal-wide' : ''}`}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        tabIndex={-1}
        onClick={(event) => event.stopPropagation()}
      >
        <header className="modal-head">
          <h3 id={titleId}>{title}</h3>
          <button type="button" className="icon-btn" onClick={onClose} aria-label="关闭">
            ×
          </button>
        </header>
        <div className="modal-body">{children}</div>
        {footer && <footer className="modal-foot">{footer}</footer>}
      </div>
    </div>
  );
}

export function Tabs({
  tabs,
  active,
  onChange,
}: {
  tabs: Array<{ key: string; label: string }>;
  active: string;
  onChange: (key: string) => void;
}) {
  const refs = useRef<Array<HTMLButtonElement | null>>([]);

  const onKeyDown = (event: ReactKeyboardEvent<HTMLButtonElement>, index: number) => {
    if (event.key !== 'ArrowRight' && event.key !== 'ArrowLeft') {
      return;
    }
    event.preventDefault();
    const delta = event.key === 'ArrowRight' ? 1 : -1;
    const next = (index + delta + tabs.length) % tabs.length;
    refs.current[next]?.focus();
    onChange(tabs[next].key);
  };

  return (
    <div className="tabs" role="tablist">
      {tabs.map((tab, index) => (
        <button
          key={tab.key}
          ref={(node) => {
            refs.current[index] = node;
          }}
          type="button"
          role="tab"
          aria-selected={tab.key === active}
          tabIndex={tab.key === active ? 0 : -1}
          className={`tab ${tab.key === active ? 'tab-active' : ''}`}
          onClick={() => onChange(tab.key)}
          onKeyDown={(event) => onKeyDown(event, index)}
        >
          {tab.label}
        </button>
      ))}
    </div>
  );
}

export function Grid({ columns = 3, children }: { columns?: number; children: ReactNode }) {
  const safe = Math.min(4, Math.max(1, columns));
  return <div className={`grid grid-cols-${safe}`}>{children}</div>;
}

export function Notice({
  tone = 'info',
  live = false,
  children,
}: {
  tone?: Tone;
  live?: boolean;
  children: ReactNode;
}) {
  return (
    <div
      className={`notice notice-${tone}`}
      role={live ? (tone === 'danger' ? 'alert' : 'status') : undefined}
      aria-live={live ? (tone === 'danger' ? 'assertive' : 'polite') : undefined}
    >
      {children}
    </div>
  );
}
