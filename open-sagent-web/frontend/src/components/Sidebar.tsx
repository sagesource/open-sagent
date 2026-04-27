import React from 'react';
import { Logo } from './Logo';
import { AgentVersionSwitch } from './AgentVersionSwitch';
import type { Conversation } from '../types';

interface SidebarProps {
  conversations: Conversation[];
  currentId?: number;
  onSelect: (conv: Conversation) => void;
  onCreate: () => void;
  onDelete: (id: number) => void;
  onUpdateTitle: (id: number, title: string) => void;
  agentVersion: 'simple' | 'smart';
  onChangeVersion: (v: 'simple' | 'smart') => void;
}

export const Sidebar: React.FC<SidebarProps> = ({
  conversations,
  currentId,
  onSelect,
  onCreate,
  onDelete,
  onUpdateTitle,
  agentVersion,
  onChangeVersion,
}) => {
  const [editingId, setEditingId] = React.useState<number | null>(null);
  const [editTitle, setEditTitle] = React.useState('');

  const startEdit = (conv: Conversation) => {
    setEditingId(conv.id);
    setEditTitle(conv.title);
  };

  const confirmEdit = (id: number) => {
    onUpdateTitle(id, editTitle);
    setEditingId(null);
  };

  return (
    <div className="sidebar">
      <Logo />
      <div className="sidebar-header">
        <button className="new-chat-btn" onClick={onCreate}>
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" style={{ marginRight: '6px', verticalAlign: 'text-bottom' }}>
            <line x1="12" y1="5" x2="12" y2="19" />
            <line x1="5" y1="12" x2="19" y2="12" />
          </svg>
          新对话
        </button>
        <AgentVersionSwitch value={agentVersion} onChange={onChangeVersion} />
      </div>
      <div className="conversation-list">
        {conversations.map((conv) => (
          <div
            key={conv.id}
            className={`conversation-item ${conv.id === currentId ? 'active' : ''}`}
            onClick={() => onSelect(conv)}
          >
            {editingId === conv.id ? (
              <input
                className="edit-title-input"
                value={editTitle}
                onChange={(e) => setEditTitle(e.target.value)}
                onBlur={() => confirmEdit(conv.id)}
                onKeyDown={(e) => {
                  if (e.key === 'Enter') confirmEdit(conv.id);
                  if (e.key === 'Escape') setEditingId(null);
                }}
                autoFocus
                onClick={(e) => e.stopPropagation()}
              />
            ) : (
              <>
                <span className="conversation-title" onDoubleClick={() => startEdit(conv)}>
                  {conv.title}
                </span>
                <span className="conversation-version">{conv.agentVersion}</span>
                <button
                  className="delete-btn"
                  onClick={(e) => {
                    e.stopPropagation();
                    onDelete(conv.id);
                  }}
                >
                  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                    <line x1="18" y1="6" x2="6" y2="18" />
                    <line x1="6" y1="6" x2="18" y2="18" />
                  </svg>
                </button>
              </>
            )}
          </div>
        ))}
      </div>
    </div>
  );
};
