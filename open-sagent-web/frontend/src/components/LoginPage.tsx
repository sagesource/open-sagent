import React, { useState } from 'react';
import { Logo } from './Logo';
import * as authApi from '../api/auth';

interface LoginPageProps {
  onLogin: (token: string) => void;
  onSwitchToRegister: () => void;
}

export const LoginPage: React.FC<LoginPageProps> = ({ onLogin, onSwitchToRegister }) => {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setLoading(true);
    try {
      const resp = await authApi.login(email, password);
      if (resp.code === 0 && resp.data?.token) {
        localStorage.setItem('token', resp.data.token);
        onLogin(resp.data.token);
      } else {
        setError(resp.message || '登录失败');
      }
    } catch (e) {
      setError('网络错误，请稍后重试');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="auth-page">
      <div className="auth-card">
        <div className="auth-header">
          <Logo />
          <p className="auth-subtitle">登录到您的 AI 助手</p>
        </div>
        <form className="auth-form" onSubmit={handleSubmit}>
          <div className="auth-field">
            <input
              type="email"
              placeholder="邮箱地址"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              required
              className="auth-input"
            />
            <div className="auth-input-line" />
          </div>
          <div className="auth-field">
            <input
              type="password"
              placeholder="密码"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
              className="auth-input"
            />
            <div className="auth-input-line" />
          </div>
          {error && <div className="auth-error">{error}</div>}
          <button type="submit" className="auth-submit" disabled={loading}>
            {loading ? (
              <span className="auth-loading">
                <svg width="16" height="16" viewBox="0 0 12 12">
                  <circle cx="6" cy="6" r="5" stroke="currentColor" strokeWidth="1.5" fill="none"
                    strokeDasharray="20" strokeDashoffset="10" style={{ animation: 'spin 1s linear infinite' }} />
                </svg>
                登录中...
              </span>
            ) : '登录'}
          </button>
        </form>
        <div className="auth-footer">
          还没有账号？
          <button className="auth-link" onClick={onSwitchToRegister}>立即注册</button>
        </div>
      </div>
    </div>
  );
};
