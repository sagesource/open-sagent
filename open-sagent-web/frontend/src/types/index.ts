export interface ApiResponse<T> {
  code: number;
  message: string;
  data: T;
}

export interface UserInfo {
  id: number;
  email: string;
  nickname: string;
}

export interface Conversation {
  id: number;
  sessionId: string;
  title: string;
  agentVersion: 'simple' | 'smart';
  updatedAt: string;
}

export interface ChatMessage {
  id: number;
  role: 'user' | 'assistant' | 'tool';
  content: string;
  createdAt: string;
}

export interface StreamEvent {
  type: 'message' | 'action' | 'title' | 'done' | 'error';
  data: string;
}
