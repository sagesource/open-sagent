# 方案：025-Web模块-修复text文本代码块按钮展示问题

## 1. 背景与目的

### 1.1 背景

在前端对话UI中，AI回复的Markdown内容会通过 `react-markdown` 解析渲染，其中的代码块由 `CodeBlock` 组件负责渲染。当前实现中，无论代码块是否有明确的编程语言标记（如 ```python），都会渲染完整的代码块头部（窗口控制点、语言标签、复制按钮）。

当模型返回普通文本块（没有指定语言，即 ```text 或无标记）时，代码块头部会占用额外空间，导致原本应紧凑展示的纯文本内容被不必要的UI元素包围，视觉上产生变形和冗余感。

### 1.2 目的

修复 `CodeBlock` 组件对普通文本块的渲染逻辑，当语言类型为 `text` 时不展示代码块头部（窗口控制点、语言标签、复制按钮），仅保留基础的语法高亮容器，提升页面内容的紧凑性和阅读体验。

## 2. 修改方案

### 2.1 文件变更列表

| 文件路径 | 变更类型 | 说明 |
|----------|----------|------|
| `frontend/src/components/CodeBlock.tsx` | 修改 | 当 `language === 'text'` 时隐藏代码块头部，仅渲染内容区域 |

### 2.2 详细变更内容

#### 文件 1: `frontend/src/components/CodeBlock.tsx`

**修改：区分 text 类型与其他语言类型的渲染逻辑**

```tsx
// 修改前
  return (
    <div className="code-block-wrapper">
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
      <SyntaxHighlighter style={style} language={language} PreTag="div">
        {code}
      </SyntaxHighlighter>
    </div>
  );

// 修改后
  const isTextBlock = language === 'text';

  return (
    <div className="code-block-wrapper">
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
      <SyntaxHighlighter style={style} language={language} PreTag="div">
        {code}
      </SyntaxHighlighter>
    </div>
  );
```

> 说明：通过判断 `language === 'text'` 控制代码块头部的渲染。当为普通文本块时，不渲染 `.code-block-header`，仅保留 `SyntaxHighlighter` 内容区域，消除不必要的UI元素对页面布局的影响。

## 3. 影响范围分析

| 范围 | 说明 |
|------|------|
| 功能逻辑 | 无影响，仅UI展示层调整 |
| 后端接口 | 无影响 |
| 前端依赖 | 无新增依赖 |
| 浏览器兼容 | 条件渲染为React基础特性，全浏览器兼容 |
| 性能 | 减少text类型代码块的DOM节点数，性能微提升 |

## 4. 测试计划

1. **代码块渲染测试**：请求模型输出带有明确语言标记的代码块（如 ```python），验证窗口控制点、语言标签、复制按钮正常展示。
2. **文本块渲染测试**：请求模型输出普通文本块（无语言标记或 ```text），验证代码块头部不展示，内容区域正常渲染。
3. **多轮对话测试**：连续进行多轮对话，混合代码块和文本块，验证各类型渲染无异常。
4. **主题切换测试**：在 light/dark 主题下分别验证 text 类型代码块的渲染效果，确保背景色和文字颜色正常。

## 5. 方案变更记录

### 变更 1（2026-04-30）：text 类型代码块宽度自适应内容

**变更原因：**
当前 text 类型代码块使用 `SyntaxHighlighter` 渲染，该组件为块级元素，默认占满父容器整行宽度。对于无语法高亮需求的普通文本块，宽度过宽导致视觉上与内容不匹配，影响阅读体验。

**文件变更：**

| 文件路径 | 变更类型 | 说明 |
|----------|----------|------|
| `frontend/src/components/CodeBlock.tsx` | 修改 | text 类型使用 `pre`/`code` 替代 `SyntaxHighlighter`，宽度随内容自适应 |
| `frontend/src/styles/global.css` | 修改 | 新增 `.text-block-pre` 样式类，设置 `display: inline-block` 实现宽度自适应 |

**关键代码变更：**

```tsx
// CodeBlock.tsx — 修改前
declare const isTextBlock: boolean;

return (
  <div className="code-block-wrapper">
    {!isTextBlock && (
      <div className="code-block-header">...</div>
    )}
    <SyntaxHighlighter style={style} language={language} PreTag="div">
      {code}
    </SyntaxHighlighter>
  </div>
);

// CodeBlock.tsx — 修改后
const isTextBlock = language === 'text';

return (
  <div className={`code-block-wrapper ${isTextBlock ? 'text-block' : ''}`}>
    {!isTextBlock && (
      <div className="code-block-header">...</div>
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
```

```css
/* global.css — 新增 */
.code-block-wrapper.text-block {
  display: inline-block;
  width: auto;
  min-width: unset;
}

.text-block-pre {
  margin: 0;
  padding: 14px 18px;
  background: transparent;
  color: var(--text-primary);
  font-family: 'SF Mono', 'Fira Code', 'Courier New', monospace;
  font-size: 14px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
}
```

### 变更 2（2026-04-30）：text 类型代码块样式轻量化

**变更原因：**
当前 text 类型代码块继承了 `.code-block-wrapper` 的卡片样式（边框、阴影、背景色），且 `padding: 14px 18px` 导致上下间距过大。这使得普通文本块在段落中显得过于突兀，像被切分出来的独立卡片，破坏了文本阅读的连贯性。

**文件变更：**

| 文件路径 | 变更类型 | 说明 |
|----------|----------|------|
| `frontend/src/styles/global.css` | 修改 | 调整 `.code-block-wrapper.text-block` 和 `.text-block-pre` 样式，移除阴影、边框和背景，减小间距 |

**关键代码变更：**

```css
/* global.css — 修改后 */
.code-block-wrapper.text-block {
  display: inline;
  width: auto;
  min-width: unset;
  margin: 0;
  padding: 0;
  border: none;
  background: transparent;
  box-shadow: none;
  border-radius: 0;
  overflow: visible;
}

.text-block-pre {
  display: inline;
  margin: 0;
  padding: 0;
  background: transparent;
  color: var(--text-primary);
  font-family: 'SF Mono', 'Fira Code', 'Courier New', monospace;
  font-size: 14.5px;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
}
```

> 说明：将 wrapper 设为 `display: inline`，移除所有卡片化样式（边框、阴影、背景、圆角），`pre` 也设为 `display: inline` 并去除 padding/margin，使 text 块完全融入正文文本流，视觉上与普通文本连贯一致，仅通过等宽字体区分。

## 6. 评审记录

| 评审人 | 时间 | 结论 | 备注 |
|--------|------|------|------|
| sage | 2026-04-30 | 通过 | 同意实施，修复text类型代码块按钮展示问题 |
| sage | 2026-04-30 | 通过 | 补充：text类型代码块宽度自适应内容 |
| sage | 2026-04-30 | 通过 | 补充：text类型代码块样式轻量化，融入文本流 |
