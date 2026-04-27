import React from 'react';

interface AgentVersionSwitchProps {
  value: 'simple' | 'smart';
  onChange: (v: 'simple' | 'smart') => void;
}

export const AgentVersionSwitch: React.FC<AgentVersionSwitchProps> = ({ value, onChange }) => {
  return (
    <div className="agent-version-switch">
      <div
        className={`switch-option ${value === 'simple' ? 'active' : ''}`}
        onClick={() => onChange('simple')}
      >
        <span className="switch-dot simple" />
        Simple
      </div>
      <div
        className={`switch-option ${value === 'smart' ? 'active' : ''}`}
        onClick={() => onChange('smart')}
      >
        <span className="switch-dot smart" />
        Smart
      </div>
      <div className="switch-slider" style={{ transform: `translateX(${value === 'simple' ? 0 : 100}%)` }} />
    </div>
  );
};
