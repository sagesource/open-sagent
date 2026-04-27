import React, { useState, useEffect } from 'react';
import { ChatPage } from './components/ChatPage';
import { LoginPage } from './components/LoginPage';
import { RegisterPage } from './components/RegisterPage';
import { TechBackground } from './components/TechBackground';
import { useTheme } from './hooks/useTheme';

const App: React.FC = () => {
  const { theme, toggle } = useTheme();
  const [authState, setAuthState] = useState<'login' | 'register' | 'chat'>('login');

  useEffect(() => {
    const token = localStorage.getItem('token');
    if (token) {
      setAuthState('chat');
    }
  }, []);

  const handleLogin = () => {
    setAuthState('chat');
  };

  const handleLogout = () => {
    localStorage.removeItem('token');
    setAuthState('login');
  };

  return (
    <>
      <TechBackground />
      {authState === 'login' && (
        <LoginPage
          onLogin={handleLogin}
          onSwitchToRegister={() => setAuthState('register')}
        />
      )}
      {authState === 'register' && (
        <RegisterPage
          onRegister={() => setAuthState('login')}
          onSwitchToLogin={() => setAuthState('login')}
        />
      )}
      {authState === 'chat' && (
        <ChatPage theme={theme} onToggleTheme={toggle} onLogout={handleLogout} />
      )}
    </>
  );
};

export default App;
