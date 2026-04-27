import React, { useState } from 'react';
import { Logo } from './Logo';
import * as authApi from '../api/auth';

interface RegisterPageProps {
  onRegister: () => void;
  onSwitchToLogin: () => void;
}

export const RegisterPage: React.FC<RegisterPageProps> = ({ onRegister, onSwitchToLogin }) => {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    if (password !== confirmPassword) {
      setError('两次输入的密码不一致');
      return;
    }
    if (password.length < 6) {
      setError('密码至少6位');
      return;
    }
    setLoading(true);
    try {
      const resp = await authApi.register(email, password);
      if (resp.code === 0) {
        onRegister();
      } else {
        setError(resp.message || '注册失败');
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
          <p className="auth-subtitle">创建您的 AI 助手账号</p>
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
              placeholder="密码（至少6位）"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
              className="auth-input"
            />
            <div className="auth-input-line" />
          </div>
          <div className="auth-field">
            <input
              type="password"
              placeholder="确认密码"
              value={confirmPassword}
              onChange={(e) => setConfirmPassword(e.target.value)}
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
                注册中...
              </span>
            ) : '注册'}
          </button>
        </form>
        <div className="auth-footer">
          已有账号？
          <button className="auth-link" onClick={onSwitchToLogin}>立即登录</button>
        </div>
      </div>
    </div>
  );
};
