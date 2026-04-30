import React from 'react';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import { CodeBlock } from './CodeBlock';
import { ActionChip } from './ActionChip';
import { normalizeStreamingMarkdown } from '../utils/markdown';
import type { ChatMessage } from '../types';

interface MessageItemProps {
  message: ChatMessage;
  theme: 'light' | 'dark';
  action?: string | null;
  isStreaming?: boolean;
}

export const MessageItem: React.FC<MessageItemProps> = ({ message, theme, action, isStreaming }) => {
  const isUser = message.role === 'user';
  const isLoading = message.role === 'loading';

  // 流式状态下对 Markdown 内容进行预处理，补全未闭合的语法结构
  const displayContent = isStreaming
    ? normalizeStreamingMarkdown(message.content)
    : message.content;

  return (
    <div className={`message-item ${isUser ? 'user' : isLoading ? 'loading' : 'assistant'}`}>
      {isUser ? (
        <div className="message-avatar-user">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" width="20" height="20">
            <circle cx="12" cy="8" r="4" />
            <path d="M4 20c0-4 4-6 8-6s8 2 8 6" />
          </svg>
        </div>
      ) : (
        <div className="message-avatar-ai">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5">
            <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2z" />
            <path d="M8 14s1.5 2 4 2 4-2 4-2" />
            <line x1="9" y1="9" x2="9.01" y2="9" />
            <line x1="15" y1="9" x2="15.01" y2="9" />
            <path d="M12 2v4M12 18v4M2 12h4M18 12h4" strokeOpacity="0.3" />
          </svg>
        </div>
      )}
      <div className={`message-content ${isUser ? 'user-content-wrapper' : ''}`}>
        {isUser ? (
          <div className="user-text">{message.content}</div>
        ) : isLoading ? (
          <div className="loading-indicator">
            <span className="loading-spinner" />
            <span className="loading-text">正在思考...</span>
          </div>
        ) : (
          <>
            {action && (
              <ActionChip action={action} />
            )}
            <ReactMarkdown
              key={isStreaming ? `streaming-${message.id}` : `final-${message.id}`}
              remarkPlugins={[remarkGfm]}
              components={{
                code(props) {
                  const { className, children } = props;
                  return (
                    <CodeBlock
                      inline={!className}
                      className={className}
                      theme={theme}
                    >
                      {children}
                    </CodeBlock>
                  );
                },
              }}
            >
              {displayContent}
            </ReactMarkdown>
            {isStreaming && <span className="typing-cursor" />}
          </>
        )}
      </div>
    </div>
  );
};
