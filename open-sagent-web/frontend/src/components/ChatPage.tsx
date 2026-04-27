import React, { useState, useEffect, useRef } from 'react';
import { MessageList } from './MessageList';
import { ChatInput } from './ChatInput';
import { Sidebar } from './Sidebar';
import { ThemeToggle } from './ThemeToggle';
import { useChatStream } from '../hooks/useChatStream';
import type { Conversation, ChatMessage } from '../types';
import * as conversationApi from '../api/conversation';

interface ChatPageProps {
  theme: 'light' | 'dark';
  onToggleTheme: () => void;
  onLogout: () => void;
}

export const ChatPage: React.FC<ChatPageProps> = ({ theme, onToggleTheme, onLogout }) => {
  const [conversations, setConversations] = useState<Conversation[]>([]);
  const [currentConv, setCurrentConv] = useState<Conversation | null>(null);
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [inputText, setInputText] = useState('');
  const [agentVersion, setAgentVersion] = useState<'simple' | 'smart'>('simple');
  const messagesEndRef = useRef<HTMLDivElement>(null);
  const { state: streamState, startStream, cancelStream } = useChatStream();

  useEffect(() => {
    conversationApi.list().then(resp => {
      if (resp.code === 0) {
        setConversations(resp.data);
      }
    });
  }, []);

  useEffect(() => {
    if (currentConv) {
      conversationApi.getMessages(currentConv.id).then(resp => {
        if (resp.code === 0) {
          setMessages(resp.data);
        }
      });
    } else {
      setMessages([]);
    }
  }, [currentConv]);

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages, streamState.content]);

  const handleCreateConv = async () => {
    const resp = await conversationApi.create(agentVersion);
    if (resp.code === 0) {
      const newConv = resp.data;
      setConversations(prev => [newConv, ...prev]);
      setCurrentConv(newConv);
    }
  };

  const handleDeleteConv = async (id: number) => {
    const resp = await conversationApi.remove(id);
    if (resp.code === 0) {
      setConversations(prev => prev.filter(c => c.id !== id));
      if (currentConv?.id === id) {
        setCurrentConv(null);
      }
    }
  };

  const handleUpdateTitle = async (id: number, title: string) => {
    const resp = await conversationApi.updateTitle(id, title);
    if (resp.code === 0) {
      setConversations(prev => prev.map(c => c.id === id ? { ...c, title } : c));
      if (currentConv?.id === id) {
        setCurrentConv(prev => prev ? { ...prev, title } : null);
      }
    }
  };

  const handleSend = async () => {
    if (!inputText.trim() || !currentConv || streamState.isStreaming) return;

    const text = inputText.trim();
    setInputText('');

    const userMsg: ChatMessage = {
      id: Date.now(),
      role: 'user',
      content: text,
      createdAt: new Date().toISOString(),
    };
    setMessages(prev => [...prev, userMsg]);

    try {
      const result = await startStream(currentConv.sessionId, text, currentConv.agentVersion);

      const assistantMsg: ChatMessage = {
        id: Date.now() + 1,
        role: 'assistant',
        content: result.content,
        createdAt: new Date().toISOString(),
      };
      setMessages(prev => [...prev, assistantMsg]);

      if (result.title && result.title !== currentConv.title) {
        handleUpdateTitle(currentConv.id, result.title);
      }
    } catch (e) {
      const errorMsg: ChatMessage = {
        id: Date.now() + 1,
        role: 'assistant',
        content: `错误：${streamState.error || '对话异常'}`,
        createdAt: new Date().toISOString(),
      };
      setMessages(prev => [...prev, errorMsg]);
    }
  };

  const handleCancel = () => {
    if (currentConv) {
      cancelStream(currentConv.sessionId);
    }
  };

  return (
    <div className="chat-layout">
      <Sidebar
        conversations={conversations}
        currentId={currentConv?.id}
        onSelect={setCurrentConv}
        onCreate={handleCreateConv}
        onDelete={handleDeleteConv}
        onUpdateTitle={handleUpdateTitle}
        agentVersion={agentVersion}
        onChangeVersion={setAgentVersion}
      />
      <div className="chat-main">
        <div className="chat-header">
          <h2>{currentConv?.title || '请选择或创建一个对话'}</h2>
          <div style={{ display: 'flex', gap: '8px', alignItems: 'center' }}>
            <ThemeToggle theme={theme} onToggle={onToggleTheme} />
            <button
              onClick={onLogout}
              className="theme-toggle"
              title="退出登录"
            >
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" style={{ verticalAlign: 'text-bottom', marginRight: '4px' }}>
                <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4" />
                <polyline points="16 17 21 12 16 7" />
                <line x1="21" y1="12" x2="9" y2="12" />
              </svg>
              退出
            </button>
          </div>
        </div>
        <div className="message-list-container">
          <MessageList
            messages={messages}
            streamingContent={streamState.isStreaming ? streamState.content : null}
            streamingAction={streamState.action}
            theme={theme}
          />
          <div ref={messagesEndRef} />
        </div>
        <ChatInput
          value={inputText}
          onChange={setInputText}
          onSend={handleSend}
          onCancel={handleCancel}
          isStreaming={streamState.isStreaming}
          disabled={!currentConv}
        />
        <div className="chat-footer">Powered by Open Sagent</div>
      </div>
    </div>
  );
};
