# 方案：020-Web模块-对话Loading动画与快捷键发送消息

## 1. 背景与目的

根据 `project-design-web` SKILL 要求：
- **需求1**：用户每发起一次对话，需要先展示一个 loading 等待响应的动画，页面不能没有任何交互
- **需求2**：用户需要使用 `command+回车`（Mac）/`ctrl+回车`（Windows）发送消息

当前代码中：
1. 用户在 `ChatInput` 中按 `Enter` 发送消息后，页面进入等待 SSE 响应的状态，但消息列表中没有任何 visual feedback 提示用户"请求已发送，正在等待响应"
2. 发送消息快捷键为 `Enter`，没有 `Shift+Enter` 换行功能

本次方案旨在优化用户体验，确保：
- 消息发送后用户能立即感知请求正在处理中
- 快捷键符合用户习惯（Command/Ctrl+Enter 发送，Enter 换行）

## 2. 修改方案

### 2.1 文件变更列表

| 文件路径 | 变更类型 | 说明 |
|----------|----------|------|
| `open-sagent-web/frontend/src/components/ChatInput.tsx` | 修改 | 修改发送快捷键为 `Command/Ctrl+Enter`；`Enter` 改为换行；placeholder 提示快捷键 |
| `open-sagent-web/frontend/src/components/ChatPage.tsx` | 修改 | 发送消息后先插入一个 loading 占位消息；SSE 响应开始后替换为流式消息 |
| `open-sagent-web/frontend/src/components/MessageItem.tsx` | 修改 | 支持渲染 loading 状态消息（旋转动画） |
| `open-sagent-web/frontend/src/hooks/useChatStream.ts` | 修改 | 新增 `isConnecting` 状态，标识 SSE 连接建立前的时间段 |
| `open-sagent-web/frontend/src/types/index.ts` | 修改 | `ChatMessage` 的 `role` 新增 `loading` 类型 |

### 2.2 详细变更内容

#### 文件 1: `open-sagent-web/frontend/src/types/index.ts`

```typescript
export interface ChatMessage {
  id: number;
  role: 'user' | 'assistant' | 'loading';
  content: string;
  createdAt: string;
}
```

#### 文件 2: `open-sagent-web/frontend/src/hooks/useChatStream.ts`

```typescript
interface ChatState {
  content: string;
  action: string | null;
  isStreaming: boolean;
  isConnecting: boolean;
  error: string | null;
}

export function useChatStream() {
  const [state, setState] = useState<ChatState>({
    content: '',
    action: null,
    isStreaming: false,
    isConnecting: false,
    error: null,
  });

  const startStream = useCallback((sessionId: string, message: string, agentVersion: string) => {
    if (eventSourceRef.current) {
      eventSourceRef.current.close();
    }

    setState({ content: '', action: null, isStreaming: false, isConnecting: true, error: null });

    // ... 建立 EventSource 连接 ...

    return new Promise<{ content: string; title?: string }>((resolve, reject) => {
      // 在第一个 message/action 事件到达时，将 isConnecting 设为 false，isStreaming 设为 true
      es.addEventListener('message', (e) => {
        fullContent += e.data;
        setState(prev => ({
          ...prev,
          content: fullContent,
          isConnecting: false,
          isStreaming: true,
        }));
      });

      es.addEventListener('action', (e) => {
        setState(prev => ({
          ...prev,
          action: e.data,
          isConnecting: false,
          isStreaming: true,
        }));
      });

      // ... done / error 处理中设置 isConnecting: false, isStreaming: false ...
    });
  }, []);

  // cancelStream 中设置 isConnecting: false
}
```

#### 文件 3: `open-sagent-web/frontend/src/components/MessageItem.tsx`

在 `MessageItem` 中新增 `role === 'loading'` 的渲染逻辑：

```tsx
export const MessageItem: React.FC<MessageItemProps> = ({ message, theme, action, isStreaming }) => {
  const isUser = message.role === 'user';
  const isLoading = message.role === 'loading';

  // ... 原有逻辑 ...

  return (
    <div className={`message-item ${isUser ? 'user' : isLoading ? 'loading' : 'assistant'}`}>
      {isLoading ? (
        <>
          <div className="message-avatar-ai">
            {/* AI avatar */}
          </div>
          <div className="message-content">
            <div className="loading-indicator">
              <span className="loading-spinner" />
              <span className="loading-text">正在思考...</span>
            </div>
          </div>
        </>
      ) : (
        // 原有 user / assistant 渲染逻辑
      )}
    </div>
  );
};
```

CSS 中新增 `.loading-indicator`、`.loading-spinner`（旋转动画）、`.loading-text` 样式。

#### 文件 4: `open-sagent-web/frontend/src/components/ChatInput.tsx`

```tsx
const handleKeyDown = (e: React.KeyboardEvent) => {
  // Command+Enter (Mac) / Ctrl+Enter (Windows) 发送消息
  if (e.key === 'Enter' && (e.metaKey || e.ctrlKey)) {
    e.preventDefault();
    if (!isStreaming) onSend();
    return;
  }

  // 单独的 Enter 键默认换行（textarea 原生行为），不做拦截
};

// placeholder 更新为提示快捷键
<textarea
  placeholder={isMac ? "Command + Enter 发送消息" : "Ctrl + Enter 发送消息"}
  // ...
/>
```

> **说明**：`textarea` 的 `Enter` 原生行为就是换行，因此只需移除原有的 `Enter` 发送拦截逻辑即可。需要检测平台是否为 Mac 来展示对应的快捷键提示。

#### 文件 5: `open-sagent-web/frontend/src/components/ChatPage.tsx`

```tsx
const handleSend = async () => {
  if (!inputText.trim() || !currentConv || streamState.isStreaming || streamState.isConnecting) return;

  const text = inputText.trim();
  setInputText('');

  const userMsg: ChatMessage = {
    id: Date.now(),
    role: 'user',
    content: text,
    createdAt: new Date().toISOString(),
  };

  const loadingMsg: ChatMessage = {
    id: Date.now() + 1,
    role: 'loading',
    content: '',
    createdAt: new Date().toISOString(),
  };

  setMessages(prev => [...prev, userMsg, loadingMsg]);

  try {
    const result = await startStream(currentConv.sessionId, text, currentConv.agentVersion);

    // SSE 响应开始后，loadingMsg 会被替换为 assistant 消息
    const assistantMsg: ChatMessage = {
      id: Date.now() + 2,
      role: 'assistant',
      content: result.content,
      createdAt: new Date().toISOString(),
    };

    // 替换 loading 消息为最终 assistant 消息
    setMessages(prev => prev.map(m => m.role === 'loading' ? assistantMsg : m));

    if (result.title && result.title !== currentConv.title) {
      handleUpdateTitle(currentConv.id, result.title);
    }
  } catch (e) {
    const errorMsg: ChatMessage = {
      id: Date.now() + 2,
      role: 'assistant',
      content: `错误：${streamState.error || '对话异常'}`,
      createdAt: new Date().toISOString(),
    };
    setMessages(prev => prev.map(m => m.role === 'loading' ? errorMsg : m));
  }
};
```

`ChatInput` 的 `disabled` 条件同步更新：
```tsx
<ChatInput
  // ...
  disabled={!currentConv || streamState.isConnecting}
/>
```

### 2.3 CSS 样式变更

在 `ChatPage` 对应的样式文件中新增 loading 消息样式（科技感主题）：

```css
.message-item.loading .message-content {
  display: flex;
  align-items: center;
}

.loading-indicator {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 0;
}

.loading-spinner {
  width: 16px;
  height: 16px;
  border: 2px solid var(--border-color);
  border-top-color: var(--accent-color);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.loading-text {
  color: var(--text-secondary);
  font-size: 14px;
}
```

## 3. 影响范围分析

| 影响点 | 说明 |
|--------|------|
| 用户体验 | 正向：发送消息后即时感知请求状态；快捷键更符合 IM 工具用户习惯 |
| 向后兼容 | 无影响：仅前端交互变更，不修改 API 接口 |
| 其他组件 | `MessageList` 无需修改，它只负责遍历渲染 `MessageItem` |
| 样式主题 | loading 动画颜色使用现有 CSS 变量（`--accent-color`、`--text-secondary`），支持明暗模式切换 |

## 4. 测试计划

| 测试项 | 步骤 | 预期结果 |
|--------|------|----------|
| Loading 动画展示 | 在对话中输入消息，按 `Command+Enter` 发送 | 消息列表中立即出现用户消息 + AI loading 消息（带旋转动画） |
| Loading 转流式 | 等待 SSE 响应 | loading 消息消失，开始流式显示 AI 回复 |
| Loading 转错误 | 模拟网络断开 | loading 消息替换为错误提示 |
| 快捷键发送（Mac） | 按 `Command+Enter` | 消息发送成功 |
| 快捷键发送（Windows） | 按 `Ctrl+Enter` | 消息发送成功 |
| Enter 换行 | 按 `Enter` | 在输入框中插入换行符，不发送消息 |
| Shift+Enter 换行 | 按 `Shift+Enter` | 在输入框中插入换行符（textarea 原生支持） |
| 连续发送拦截 | 在 loading 状态下尝试再次发送 | 输入框置灰，不可发送 |
| 明暗模式 | 切换主题 | loading 动画颜色随主题变化 |

## 5. 评审记录

| 评审人 | 时间 | 结论 | 备注 |
|--------|------|------|------|
| sage.xue | 2026-04-28 | 通过 | |
