import React from 'react';
import { MessageItem } from './MessageItem';
import type { ChatMessage } from '../types';

interface MessageListProps {
  messages: ChatMessage[];
  streamingContent: string | null;
  streamingAction: string | null;
  theme: 'light' | 'dark';
}

export const MessageList: React.FC<MessageListProps> = ({
  messages,
  streamingContent,
  streamingAction,
  theme,
}) => {
  return (
    <div className="message-list">
      {messages.map((msg) => (
        msg.role === 'loading' && streamingContent ? null : (
          <MessageItem key={msg.id} message={msg} theme={theme} />
        )
      ))}
      {streamingContent && (
        <MessageItem
          message={{
            id: -1,
            role: 'assistant',
            content: streamingContent,
            createdAt: new Date().toISOString(),
          }}
          theme={theme}
          action={streamingAction}
          isStreaming={true}
        />
      )}
    </div>
  );
};
