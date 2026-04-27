import type { ApiResponse, UserInfo } from '../types';

const headers = () => ({
  'Content-Type': 'application/json',
  Authorization: `Bearer ${localStorage.getItem('token') || ''}`,
});

export async function register(email: string, password: string): Promise<ApiResponse<void>> {
  const res = await fetch('/api/auth/register', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, password }),
  });
  return res.json();
}

export async function login(email: string, password: string): Promise<ApiResponse<{ token: string }>> {
  const res = await fetch('/api/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, password }),
  });
  return res.json();
}

export async function getUserInfo(): Promise<ApiResponse<UserInfo>> {
  const res = await fetch('/api/auth/me', { headers: headers() });
  return res.json();
}

export async function updateProfile(nickname?: string, password?: string): Promise<ApiResponse<UserInfo>> {
  const res = await fetch('/api/auth/profile', {
    method: 'PUT',
    headers: headers(),
    body: JSON.stringify({ nickname, password }),
  });
  return res.json();
}
