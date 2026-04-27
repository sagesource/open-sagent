import React from 'react';

interface ActionChipProps {
  action: string;
}

export const ActionChip: React.FC<ActionChipProps> = ({ action }) => {
  // 解析 AGENT_ACTION[EXECUTE_TOOL]:工具名 格式
  const displayText = action.replace(/^AGENT_ACTION\[.*?\]:?/, '');
  const actionType = action.match(/AGENT_ACTION\[(.*?)\]/)?.[1] || 'ACTION';

  return (
    <div className="action-chip">
      <span className="action-chip-spinner">
        <svg width="12" height="12" viewBox="0 0 12 12">
          <circle
            cx="6"
            cy="6"
            r="5"
            stroke="currentColor"
            strokeWidth="1.5"
            fill="none"
            strokeDasharray="20"
            strokeDashoffset="10"
            style={{ animation: 'spin 1s linear infinite' }}
          />
        </svg>
      </span>
      <span className="action-chip-label">{actionType}</span>
      {displayText && <span className="action-chip-detail">{displayText}</span>}
    </div>
  );
};
