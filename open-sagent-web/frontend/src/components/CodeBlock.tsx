import React, { useState } from 'react';
import { Prism as SyntaxHighlighter } from 'react-syntax-highlighter';
import { oneLight, oneDark } from 'react-syntax-highlighter/dist/esm/styles/prism';

interface CodeBlockProps {
  inline?: boolean;
  className?: string;
  children: React.ReactNode;
  theme?: 'light' | 'dark';
}

export const CodeBlock: React.FC<CodeBlockProps> = ({ inline, className, children, theme = 'dark' }) => {
  const [copied, setCopied] = useState(false);
  const match = /language-(\w+)/.exec(className || '');
  const language = match ? match[1] : 'text';
  const code = String(children).replace(/\n$/, '');
  const style = theme === 'dark' ? oneDark : oneLight;

  const handleCopy = () => {
    navigator.clipboard.writeText(code).then(() => {
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    });
  };

  if (inline) {
    return <code className="inline-code">{children}</code>;
  }

  const isTextBlock = language === 'text';

  return (
    <div className={`code-block-wrapper ${isTextBlock ? 'text-block' : ''}`}>
      {!isTextBlock && (
        <div className="code-block-header">
          <div className="code-block-window-controls">
            <span className="code-block-dot red" />
            <span className="code-block-dot yellow" />
            <span className="code-block-dot green" />
          </div>
          <span className="code-language">{language}</span>
          <button className="copy-button" onClick={handleCopy}>
            {copied ? (
              <span style={{ display: 'flex', alignItems: 'center', gap: '4px' }}>
                <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                  <polyline points="20 6 9 17 4 12" />
                </svg>
                已复制
              </span>
            ) : '复制'}
          </button>
        </div>
      )}
      {isTextBlock ? (
        <pre className="text-block-pre"><code>{code}</code></pre>
      ) : (
        <SyntaxHighlighter style={style} language={language} PreTag="div">
          {code}
        </SyntaxHighlighter>
      )}
    </div>
  );
};
