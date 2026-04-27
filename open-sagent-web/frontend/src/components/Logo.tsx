import React from 'react';

export const Logo: React.FC = () => {
  return (
    <div className="logo-wrapper">
      <svg width="28" height="28" viewBox="0 0 28 28" fill="none" className="logo-icon">
        <defs>
          <linearGradient id="logoGrad" x1="0%" y1="0%" x2="100%" y2="100%">
            <stop offset="0%" stopColor="#38bdf8" />
            <stop offset="100%" stopColor="#818cf8" />
          </linearGradient>
          <filter id="logoGlow">
            <feGaussianBlur stdDeviation="2" result="coloredBlur" />
            <feMerge>
              <feMergeNode in="coloredBlur" />
              <feMergeNode in="SourceGraphic" />
            </feMerge>
          </filter>
        </defs>
        <path
          d="M14 2L25 8.5V19.5L14 26L3 19.5V8.5L14 2Z"
          stroke="url(#logoGrad)"
          strokeWidth="1.5"
          fill="none"
          filter="url(#logoGlow)"
        />
        <circle cx="14" cy="14" r="4" fill="url(#logoGrad)" filter="url(#logoGlow)" />
        <circle cx="14" cy="14" r="1.5" fill="#0a0e1a" />
      </svg>
      <span className="logo-text">Sagent</span>
    </div>
  );
};
