import React from 'react';

export const TechBackground: React.FC = () => {
  return (
    <div className="tech-background">
      <div className="tech-glow" />
      <div className="tech-particles">
        {Array.from({ length: 12 }).map((_, i) => (
          <div
            key={i}
            className="tech-particle"
            style={{
              left: `${Math.random() * 100}%`,
              top: `${Math.random() * 100}%`,
              animationDelay: `${Math.random() * 8}s`,
              animationDuration: `${6 + Math.random() * 6}s`,
            }}
          />
        ))}
      </div>
    </div>
  );
};
