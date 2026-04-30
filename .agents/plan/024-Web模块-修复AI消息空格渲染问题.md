# 方案：024-Web模块-修复AI消息空格渲染问题

## 1. 背景与目的

### 1.1 背景

在排查"Spring SSE输出时空格被抹去"问题时，代码审查确认 SSE 传输层（后端 `ChatService.java`、前端 `useChatStream.ts`）均无 `trim()` 操作，空格在传输过程中被完整保留。

但前端渲染层存在以下问题导致空格视觉上"丢失"：

1. **AI 消息 Markdown 内容未保留空白字符**：`global.css` 中未对 AI 消息的 `.message-content` 设置 `white-space: pre-wrap`，浏览器默认会将连续空格合并为单个空格，并忽略文本节点的首尾空格。
2. **用户输入被 trim**：`ChatPage.tsx:81` 中 `const text = inputText.trim();` 会去除用户有意输入的首尾空格。

### 1.2 目的

修复上述空格渲染问题，确保 AI 回复内容和用户输入中的空格（包括代码缩进、首行缩进等）能够正确显示。

## 2. 修改方案

### 2.1 文件变更列表

| 文件路径 | 变更类型 | 说明 |
|----------|----------|------|
| `frontend/src/styles/global.css` | 修改 | 为 AI 消息 `.message-content` 增加 `white-space: pre-wrap`，保留空格 |
| `frontend/src/components/ChatPage.tsx` | 修改 | 移除用户输入的 `trim()`，保留原始输入内容 |

### 2.2 详细变更内容

#### 文件 1: `frontend/src/styles/global.css`

**修改 AI 消息内容保留空格**

```css
/* 修改前 */
.message-content {
  flex: 1;
  max-width: calc(100% - 50px);
  padding: 14px 18px;
  border-radius: 16px;
  line-height: 1.7;
  font-size: 14.5px;
}

/* 修改后 */
.message-content {
  flex: 1;
  max-width: calc(100% - 50px);
  padding: 14px 18px;
  border-radius: 16px;
  line-height: 1.7;
  font-size: 14.5px;
  white-space: pre-wrap;
  word-break: break-word;
}
```

> 说明：`white-space: pre-wrap` 保留文本中的空格和换行符，同时允许文本在边界处自动换行；`word-break: break-word` 防止超长无空格文本撑破布局。用户消息 `.user-text` 已有相同样式，AI 消息需要补充。

#### 文件 2: `frontend/src/components/ChatPage.tsx`

**移除用户输入 trim**

```tsx
// 修改前（第79-81行）
    if (!inputText.trim() || !currentConv || streamState.isStreaming || streamState.isConnecting) return;

    const text = inputText.trim();
    setInputText('');

// 修改后
    if (!inputText || !currentConv || streamState.isStreaming || streamState.isConnecting) return;

    const text = inputText;
    setInputText('');
```

> 说明：移除两处 `trim()`，保留用户原始输入内容（包括首尾空格）。空内容检查改为 `!inputText`（空字符串为 falsy），与原有 `trim()` 判空效果一致。

## 3. 影响范围分析

| 范围 | 说明 |
|------|------|
| 功能逻辑 | 无影响，仅保留原始输入内容和渲染空格 |
| 后端接口 | 无影响 |
| 前端依赖 | 无新增依赖 |
| 浏览器兼容 | `white-space: pre-wrap` 为 CSS 基础特性，全浏览器兼容 |
| 性能 | 无影响 |

## 4. 测试计划

1. **AI 消息空格保留测试**：请求模型输出包含多个连续空格的内容（如代码缩进），验证空格正确显示，不被合并。
2. **用户输入空格保留测试**：在输入框中输入以空格开头或结尾的消息，验证发送后消息内容保留首尾空格。
3. **多轮对话测试**：连续进行多轮对话，验证消息渲染无异常，换行和空格行为正常。
4. **长文本测试**：发送超长无空格文本，验证布局不溢出。

## 5. 实施后问题修复

### 5.1 问题1：回滚用户输入trim移除（用户要求保留trim）

**评审意见**：用户要求对用户输入保留trim行为，回滚plan-24中对ChatPage.tsx的trim移除修改。

**修复方案**：恢复`ChatPage.tsx`为`if (!inputText.trim() || ...) return;`及`const text = inputText.trim();`。

### 5.2 问题2：AI回复SSE内容前导空格丢失

**现象**：AI回复中代码缩进等前导空格仍然丢失。

**根因**：Spring的`SseEventBuilderImpl`生成SSE格式为`data:<content>`（冒号后无空格分隔符）。根据W3C EventSource规范，浏览器会移除`data:`后的**第一个前导空格**。当AI回复的chunk内容以空格开头时，这个前导空格被SSE规范"吃掉"导致丢失。

**修复方案**：SSE data字段改用JSON格式传输——后端用`JSON.toJSONString()`包装内容，JSON字符串以双引号开头不会被EventSource移除空格；前端用`JSON.parse()`解析。

**变更文件**：

| 文件路径 | 变更类型 | 说明 |
|----------|----------|------|
| `frontend/src/components/ChatInput.tsx` | 修改 | 禁用检查`!value.trim()`改为`!value` |
| `frontend/src/hooks/useChatStream.ts` | 修改 | 所有event data添加`JSON.parse()`解析 |
| `src/main/java/.../service/ChatService.java` | 修改 | 所有SSE event的data字段用`JSON.toJSONString()`包装 |

## 6. 评审记录

| 评审人 | 时间 | 结论 | 备注 |
|--------|------|------|------|
| sage | 2026-04-30 | 通过 | 同意实施，修复AI消息空格渲染问题 |
| sage | 2026-04-30 | 实施完成 | 已修改 global.css 和 ChatPage.tsx |
| sage | 2026-04-30 | 修正评审 | 问题1回滚trim（用户要求保留trim）；问题2同意实施SSE JSON传输方案 |
| sage | 2026-04-30 | 实施完成 | 已恢复ChatPage.tsx trim，已实施SSE JSON传输（ChatService.java + useChatStream.ts） |
