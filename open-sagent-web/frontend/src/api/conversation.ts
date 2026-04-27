import type { ApiResponse, Conversation, ChatMessage } from '../types';

const headers = () => ({
  'Content-Type': 'application/json',
  Authorization: `Bearer ${localStorage.getItem('token') || ''}`,
});

export async function list(): Promise<ApiResponse<Conversation[]>> {
  const res = await fetch('/api/conversations', { headers: headers() });
  return res.json();
}

export async function create(agentVersion: string): Promise<ApiResponse<Conversation>> {
  const res = await fetch('/api/conversations', {
    method: 'POST',
    headers: headers(),
    body: JSON.stringify({ agentVersion }),
  });
  return res.json();
}

export async function remove(id: number): Promise<ApiResponse<void>> {
  const res = await fetch(`/api/conversations/${id}`, {
    method: 'DELETE',
    headers: headers(),
  });
  return res.json();
}

export async function updateTitle(id: number, title: string): Promise<ApiResponse<void>> {
  const res = await fetch(`/api/conversations/${id}/title`, {
    method: 'PUT',
    headers: headers(),
    body: JSON.stringify({ title }),
  });
  return res.json();
}

export async function getMessages(id: number): Promise<ApiResponse<ChatMessage[]>> {
  const res = await fetch(`/api/conversations/${id}/messages`, { headers: headers() });
  return res.json();
}
