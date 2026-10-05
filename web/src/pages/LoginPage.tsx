import { useState } from 'react';
import { Navigate, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { Button, Field, Input, Select } from '../components/ui';

const ROLES = ['ADMIN', 'RISK_OFFICER', 'TRADER', 'FUND_MANAGER', 'COMPLIANCE', 'VIEWER'];

export default function LoginPage() {
  const { session, login } = useAuth();
  const navigate = useNavigate();
  const [role, setRole] = useState('ADMIN');
  const [userId, setUserId] = useState('admin');
  const [group, setGroup] = useState('');

  if (session) {
    return <Navigate to="/dashboard" replace />;
  }

  const submit = () => {
    login({ role, userId: userId.trim() || 'anonymous', group: group.trim() });
    navigate('/dashboard');
  };

  return (
    <div className="login-shell">
      <div className="login-card">
        <div className="login-brand">
          <span className="brand-mark">LH</span>
          <div>
            <h1>lhjypt 量化交易管理平台</h1>
            <p className="muted">本地鉴权模拟 · 选择角色进入控制台</p>
          </div>
        </div>
        <Field label="角色（X-User-Role）">
          <Select value={role} onChange={(event) => setRole(event.target.value)}>
            {ROLES.map((item) => (
              <option key={item} value={item}>
                {item}
              </option>
            ))}
          </Select>
        </Field>
        <Field label="用户 ID（X-User-Id）">
          <Input value={userId} onChange={(event) => setUserId(event.target.value)} placeholder="admin" />
        </Field>
        <Field label="用户组（X-User-Group，可选）">
          <Input value={group} onChange={(event) => setGroup(event.target.value)} placeholder="group-a" />
        </Field>
        <Button variant="primary" onClick={submit}>
          进入控制台
        </Button>
        <p className="login-hint muted">
          提示：写操作需 TRADER/ADMIN 等角色；强平、再平衡、下载等敏感操作会签发一次性确认令牌。
        </p>
      </div>
    </div>
  );
}
