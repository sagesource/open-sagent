import { useState, useRef, useCallback } from 'react';

interface ChatState {
  content: string;
  action: string | null;
  isStreaming: boolean;
  isConnecting: boolean;
  error: string | null;
}

export function useChatStream() {
  const [state, setState] = useState<ChatState>({
    content: '',
    action: null,
    isStreaming: false,
    isConnecting: false,
    error: null,
  });
  const eventSourceRef = useRef<EventSource | null>(null);
  const rejectRef = useRef<((reason?: any) => void) | null>(null);

  const startStream = useCallback((sessionId: string, message: string, agentVersion: string) => {
    if (eventSourceRef.current) {
      eventSourceRef.current.close();
    }

    setState({ content: '', action: null, isStreaming: false, isConnecting: true, error: null });

    const token = localStorage.getItem('token') || '';
    const params = new URLSearchParams({ sessionId, message, agentVersion, token });
    const es = new EventSource(`/api/chat/stream?${params.toString()}`, {
      withCredentials: false,
    });
    eventSourceRef.current = es;

    // 由于EventSource不支持自定义headers，需要在URL中传递token或使用cookie
    // 这里简化处理，实际项目中可以通过cookie或proxy处理认证

    return new Promise<{ content: string; title?: string }>((resolve, reject) => {
      rejectRef.current = reject;
      let fullContent = '';
      let title: string | undefined;

      es.addEventListener('message', (e) => {
        fullContent += e.data;
        setState(prev => ({ ...prev, content: fullContent, isConnecting: false, isStreaming: true }));
      });

      es.addEventListener('action', (e) => {
        setState(prev => ({ ...prev, action: e.data, isConnecting: false, isStreaming: true }));
      });

      es.addEventListener('title', (e) => {
        title = e.data;
      });

      es.addEventListener('done', () => {
        es.close();
        setState(prev => ({ ...prev, isStreaming: false, isConnecting: false }));
        rejectRef.current = null;
        resolve({ content: fullContent, title });
      });

      es.addEventListener('error', (e) => {
        const data = (e as MessageEvent).data || '连接异常';
        es.close();
        setState(prev => ({ ...prev, isStreaming: false, isConnecting: false, error: data }));
        rejectRef.current = null;
        reject(new Error(data));
      });

      es.onerror = () => {
        es.close();
        setState(prev => ({ ...prev, isStreaming: false, isConnecting: false, error: '连接已断开' }));
        rejectRef.current = null;
        reject(new Error('连接已断开'));
      };
    });
  }, []);

  const cancelStream = useCallback((sessionId: string) => {
    if (rejectRef.current) {
      rejectRef.current(new Error('用户已取消'));
      rejectRef.current = null;
    }
    if (eventSourceRef.current) {
      eventSourceRef.current.close();
    }
    fetch(`/api/chat/${sessionId}/cancel`, {
      method: 'POST',
      headers: { Authorization: `Bearer ${localStorage.getItem('token')}` },
    });
    setState(prev => ({ ...prev, isStreaming: false, isConnecting: false }));
  }, []);

  return { state, startStream, cancelStream };
}
