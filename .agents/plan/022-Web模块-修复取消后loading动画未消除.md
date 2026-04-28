# 方案：022-Web模块-修复取消后loading动画未消除

## 1. 背景与目的

### 1.1 问题背景

plan-021 修复了 SSE 中断后后端报连环异常的 BUG，但在前端仍存在一个问题：
- 当用户点击"停止"按钮主动取消对话时，或者 SSE 连接异常中断时，对话列表中的 loading 消息（动画）没有被替换，一直停留在界面上。

### 1.2 修复目的

- 用户主动取消对话后，loading 消息应被替换为提示消息或移除
- SSE 异常中断后，loading 消息应被替换为错误提示

## 2. 修改方案

### 2.1 问题根因分析

```
用户点击"停止"按钮
    ↓
ChatPage.handleCancel() 调用 cancelStream()
    ↓
cancelStream() 调用 eventSource.close() 并设置状态 isStreaming=false
    ↓
但是 startStream() 返回的 Promise 仍处于 pending 状态，从未 resolve/reject
    ↓
ChatPage.handleSend() 中的 await startStream(...) 一直等待
    ↓
try/catch 的 catch 分支永远无法执行
    ↓
loading 消息永远不会被替换，一直显示在界面上
```

根因：`cancelStream()` 关闭了 EventSource，但没有通知 `startStream()` 返回的 Promise，导致 Promise 永远挂起。

### 2.2 修复策略

1. **useChatStream.ts**：引入 `rejectRef` 保存当前 pending Promise 的 reject 函数
2. **useChatStream.ts**：在 `cancelStream()` 中，如果存在 pending 的 Promise，主动 reject 它
3. **useChatStream.ts**：在 Promise 正常完成（resolve/reject）后，清空 `rejectRef`

### 2.3 文件变更列表

| 文件路径 | 变更类型 | 说明 |
|----------|----------|------|
| `open-sagent-web/frontend/src/hooks/useChatStream.ts` | 修改 | 修复 cancelStream 后 Promise 不结束的问题 |

### 2.4 详细变更内容

#### 文件: useChatStream.ts

**变更点 1：增加 rejectRef 保存 pending Promise 的 reject 函数**

```typescript
export function useChatStream() {
  // ... 原有 state 和 eventSourceRef 不变 ...
  const eventSourceRef = useRef<EventSource | null>(null);
  // 新增：保存当前 pending Promise 的 reject 函数，用于 cancelStream 时主动结束 Promise
  const rejectRef = useRef<((reason?: any) => void) | null>(null);
```

**变更点 2：startStream 中在 Promise 创建时记录 reject 函数，完成时清空**

```typescript
  const startStream = useCallback((sessionId: string, message: string, agentVersion: string) => {
    if (eventSourceRef.current) {
      eventSourceRef.current.close();
    }

    setState({ content: '', action: null, isStreaming: false, isConnecting: true, error: null });

    const token = localStorage.getItem('token') || '';
    const params = new URLSearchParams({ sessionId, message, agentVersion, token });
    const es = new EventSource(`/api/chat/stream?${params.toString()}`, {
      withCredentials: false,
    });
    eventSourceRef.current = es;

    return new Promise<{ content: string; title?: string }>((resolve, reject) => {
      // 新增：记录 reject 函数，供 cancelStream 使用
      rejectRef.current = reject;
      let fullContent = '';
      let title: string | undefined;

      es.addEventListener('message', (e) => {
        fullContent += e.data;
        setState(prev => ({ ...prev, content: fullContent, isConnecting: false, isStreaming: true }));
      });

      es.addEventListener('action', (e) => {
        setState(prev => ({ ...prev, action: e.data, isConnecting: false, isStreaming: true }));
      });

      es.addEventListener('title', (e) => {
        title = e.data;
      });

      es.addEventListener('done', () => {
        es.close();
        setState(prev => ({ ...prev, isStreaming: false, isConnecting: false }));
        // 新增：清空 rejectRef
        rejectRef.current = null;
        resolve({ content: fullContent, title });
      });

      es.addEventListener('error', (e) => {
        const data = (e as MessageEvent).data || '连接异常';
        es.close();
        setState(prev => ({ ...prev, isStreaming: false, isConnecting: false, error: data }));
        // 新增：清空 rejectRef
        rejectRef.current = null;
        reject(new Error(data));
      });

      es.onerror = () => {
        es.close();
        setState(prev => ({ ...prev, isStreaming: false, isConnecting: false, error: '连接已断开' }));
        // 新增：清空 rejectRef
        rejectRef.current = null;
        reject(new Error('连接已断开'));
      };
    });
  }, []);
```

**变更点 3：cancelStream 中主动 reject pending Promise**

```typescript
  const cancelStream = useCallback((sessionId: string) => {
    // 新增：如果有 pending 的 Promise，先 reject 它，让调用方能进入 catch 分支
    if (rejectRef.current) {
      rejectRef.current(new Error('用户已取消'));
      rejectRef.current = null;
    }
    if (eventSourceRef.current) {
      eventSourceRef.current.close();
    }
    fetch(`/api/chat/${sessionId}/cancel`, {
      method: 'POST',
      headers: { Authorization: `Bearer ${localStorage.getItem('token')}` },
    });
    setState(prev => ({ ...prev, isStreaming: false, isConnecting: false }));
  }, []);
```

## 3. 影响范围分析

| 影响项 | 影响程度 | 说明 |
|--------|----------|------|
| 主动取消对话 | 中 | 取消后 loading 消息会被正确替换 |
| SSE 异常中断 | 低 | 已有 onerror 处理，本次只是补充 cancel 场景 |
| 正常对话流程 | 无 | 正常完成时行为不变 |

## 4. 测试计划

1. **正常对话测试**：发起对话，确认正常完成后 loading 被替换为 assistant 消息
2. **主动取消测试**：对话过程中点击"停止"按钮，确认 loading 消息被替换
3. **网络中断测试**：对话过程中断开网络，确认 loading 消息被替换为错误提示

## 5. 评审记录

| 评审人 | 时间 | 结论 | 备注 |
|--------|------|------|------|
| sage.xue | 2026-04-28 | 通过 | |
