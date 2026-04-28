# 方案：019-Web模块-修复Markdown流式渲染异常

## 1. 背景与目的

### 1.1 背景

当前对话页面在 SSE 流式输出过程中，`MessageItem` 组件使用 `react-markdown` 实时解析正在增量接收的 Markdown 内容。由于流式输出时内容是不完整的（最常见的是代码块只有开始标记 ```` ``` ```` 没有结束标记），`react-markdown` 无法正确解析不完整的 Markdown 语法，导致以下现象：

- 代码块在流式过程中，因缺少闭合标记，导致后续所有内容被当作代码内容渲染，格式严重错乱
- 刷新页面后，消息从后端完整加载，此时 Markdown 内容闭合标记齐全，可以正确渲染展示

### 1.2 目的

修复流式输出时 Markdown 渲染不一致的问题，确保：
1. 流式输出过程中，Markdown 语法能够被实时正确渲染
2. 不会出现"只渲染一部分"的格式错乱情况（如代码块未闭合导致后续内容全被当作代码）
3. Markdown 内容输出完成时，页面也同步完成正确的全部渲染，无需刷新页面

## 2. 修改方案

### 2.1 核心设计思路

**流式 Markdown 智能预处理**：在流式输出过程中，对助手消息内容进行智能预处理，自动补全未闭合的 Markdown 语法结构（主要是代码块），使 `react-markdown` 始终接收到语法完整的内容，从而能够正确实时渲染。

当流式结束后，内容本身是完整的，预处理不会改变原始内容的渲染结果。

### 2.2 文件变更列表

| 文件路径 | 变更类型 | 说明 |
|----------|----------|------|
| `open-sagent-web/frontend/src/utils/markdown.ts` | 新增 | Markdown 流式预处理工具函数 |
| `open-sagent-web/frontend/src/components/MessageItem.tsx` | 修改 | 流式状态下先预处理内容，再使用 react-markdown 渲染 |

### 2.3 详细变更内容

#### 文件 1: `open-sagent-web/frontend/src/utils/markdown.ts`（新增）

```typescript
/**
 * 对流式输出的 Markdown 内容进行预处理，自动补全未闭合的语法结构，
 * 使 react-markdown 能够正确解析和实时渲染。
 *
 * 主要处理：
 * 1. 未闭合的代码块（```）：补充闭合标记
 * 2. 未闭合的行内代码（`）：补充闭合标记
 */
export function normalizeStreamingMarkdown(content: string): string {
  if (!content || content.trim() === '') {
    return content;
  }

  let normalized = content;

  // 1. 处理未闭合的代码块
  // 统计 ``` 出现的次数，奇数表示存在未闭合的代码块
  const fenceMatches = normalized.match(/```/g);
  if (fenceMatches && fenceMatches.length % 2 === 1) {
    // 补充换行和闭合标记，使代码块能够正确渲染
    if (!normalized.endsWith('\n')) {
      normalized += '\n';
    }
    normalized += '```';
  }

  // 2. 处理未闭合的行内代码
  // 统计单个反引号（排除三个一组的代码块标记）
  const backtickMatches = normalized.match(/(?<!`)`(?!`)/g);
  if (backtickMatches && backtickMatches.length % 2 === 1) {
    normalized += '`';
  }

  return normalized;
}
```

#### 文件 2: `open-sagent-web/frontend/src/components/MessageItem.tsx`（修改）

```typescript
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

  // 流式状态下对 Markdown 内容进行预处理，补全未闭合的语法结构
  const displayContent = isStreaming
    ? normalizeStreamingMarkdown(message.content)
    : message.content;

  return (
    <div className={`message-item ${isUser ? 'user' : 'assistant'}`}>
      {isUser ? (
        <div className="message-avatar-user">
          {message.content.charAt(0).toUpperCase()}
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
      <div className="message-content">
        {isUser ? (
          <div className="user-text">{message.content}</div>
        ) : (
          <>
            {action && (
              <ActionChip action={action} />
            )}
            <ReactMarkdown
              remarkPlugins={[remarkGfm]}
              components={{
                code(props) {
                  const { inline, className, children } = props;
                  return (
                    <CodeBlock
                      inline={inline}
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
```

**关键变更说明：**
- 新增 `normalizeStreamingMarkdown` 工具函数，自动补全未闭合的代码块和行内代码标记
- `MessageItem` 在流式状态下，先对 `message.content` 进行预处理，再将处理后的内容传递给 `ReactMarkdown`
- 流式结束后（`isStreaming=false`），使用原始内容渲染，此时内容本身已完整，无需预处理
- 预处理仅在流式过程中生效，不影响历史消息的渲染

### 2.4 预处理逻辑说明

| 场景 | 原始内容（流式中） | 预处理后 | 渲染结果 |
|------|-------------------|----------|----------|
| 代码块未闭合 | ```` ```java\npublic class ` | ```` ```java\npublic class \n``` ```` | 代码块正确渲染，后续内容不会错乱 |
| 代码块已闭合 | ```` ```java\npublic class {}\n``` ```` | 不变 | 代码块正确渲染 |
| 行内代码未闭合 | `` `hello `` | `` `hello` `` | 行内代码正确渲染 |
| 普通文本 | `hello world` | 不变 | 普通文本渲染 |

## 3. 影响范围分析

| 范围 | 影响说明 |
|------|----------|
| 流式对话体验 | 流式过程中 Markdown 语法（代码块、行内代码）能够实时正确渲染，不再出现格式错乱 |
| 历史消息展示 | 无影响，历史消息 `isStreaming` 为 `false`，直接使用原始内容渲染 |
| 流式结束后展示 | 流式结束后内容本身已完整，`normalizeStreamingMarkdown` 不会添加额外标记，渲染结果与刷新后一致 |
| 其他组件 | 仅新增 `utils/markdown.ts` 和修改 `MessageItem.tsx`，不影响其他组件逻辑 |

## 4. 测试计划

### 4.1 手动验证清单

- [ ] 流式对话时代码块实时正确渲染，后续内容不被当作代码
- [ ] 流式对话时行内代码实时正确渲染
- [ ] 流式对话时列表、表格、粗体等 Markdown 语法实时正确渲染
- [ ] 流式结束后，代码块渲染结果与刷新页面后一致
- [ ] 历史消息加载后 Markdown 渲染正确
- [ ] 暗/亮模式下样式正常

### 4.2 测试场景

| 场景 | 输入内容 | 流式中预期 | 流式后预期 |
|------|----------|-----------|-----------|
| 代码块 | ```` ```java\npublic class Hello {} ```` | 代码块正确渲染，后续文本不进入代码块 | 代码块完整渲染，含语言标签和复制按钮 |
| 混合语法 | `# 标题\n```\ncode\n```\n**粗体**` | 标题、代码块、粗体均正确渲染 | 与流式中最终状态一致 |
| 行内代码 | `` `code` 和文本 `` | 行内代码正确渲染 | 行内代码正确渲染 |
| 无 Markdown | `hello world` | 普通文本展示 | 普通文本展示 |

## 5. 评审记录

| 评审人 | 时间 | 结论 | 备注 |
|--------|------|------|------|
| sage | 2026-04-28 | 需修改 | 需要实时渲染 markdown 语法，同时要避免只渲染一部分的情况，应该是 markdown 的内容输出完，页面也实时的完成全部渲染 |
| sage | 2026-04-28 | 通过 | 同意实施 |
