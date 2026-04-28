import { useState, useRef, useCallback } from 'react';

interface ChatState {
  content: string;
  action: string | null;
  isStreaming: boolean;
  error: string | null;
}

export function useChatStream() {
  const [state, setState] = useState<ChatState>({
    content: '',
    action: null,
    isStreaming: false,
    error: null,
  });
  const eventSourceRef = useRef<EventSource | null>(null);

  const startStream = useCallback((sessionId: string, message: string, agentVersion: string) => {
    if (eventSourceRef.current) {
      eventSourceRef.current.close();
    }

    setState({ content: '', action: null, isStreaming: true, error: null });

    const token = localStorage.getItem('token') || '';
    const params = new URLSearchParams({ sessionId, message, agentVersion, token });
    const es = new EventSource(`/api/chat/stream?${params.toString()}`, {
      withCredentials: false,
    });
    eventSourceRef.current = es;

    // 由于EventSource不支持自定义headers，需要在URL中传递token或使用cookie
    // 这里简化处理，实际项目中可以通过cookie或proxy处理认证

    return new Promise<{ content: string; title?: string }>((resolve, reject) => {
      let fullContent = '';
      let title: string | undefined;

      es.addEventListener('message', (e) => {
        fullContent += e.data;
        setState(prev => ({ ...prev, content: fullContent }));
      });

      es.addEventListener('action', (e) => {
        setState(prev => ({ ...prev, action: e.data }));
      });

      es.addEventListener('title', (e) => {
        title = e.data;
      });

      es.addEventListener('done', () => {
        es.close();
        setState(prev => ({ ...prev, isStreaming: false }));
        resolve({ content: fullContent, title });
      });

      es.addEventListener('error', (e) => {
        const data = (e as MessageEvent).data || '连接异常';
        es.close();
        setState(prev => ({ ...prev, isStreaming: false, error: data }));
        reject(new Error(data));
      });

      es.onerror = () => {
        es.close();
        setState(prev => ({ ...prev, isStreaming: false, error: '连接已断开' }));
        reject(new Error('连接已断开'));
      };
    });
  }, []);

  const cancelStream = useCallback((sessionId: string) => {
    if (eventSourceRef.current) {
      eventSourceRef.current.close();
    }
    fetch(`/api/chat/${sessionId}/cancel`, {
      method: 'POST',
      headers: { Authorization: `Bearer ${localStorage.getItem('token')}` },
    });
    setState(prev => ({ ...prev, isStreaming: false }));
  }, []);

  return { state, startStream, cancelStream };
}
