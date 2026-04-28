# Error日志输出规范优化方案

## 方案编号
016

## 方案描述
根据 `technology_stack.md` 编码规范中的「代码日志规范」，对全项目error级别日志进行规范化优化。

## 规范依据

编码规范中日志格式要求：
```
log.error("> 模块 | id:{} | 执行异常: <", id, e)
```

核心要点：
1. 以 `> ` 开头，以 ` <` 结尾
2. 包含模块标识名
3. **异常对象 `e` 必须作为最后一个参数传入**，由Logback自动输出完整堆栈信息
4. **禁止在日志消息中使用 `e.getMessage()` 作为占位符**，因为异常对象传入后message已自动包含

## 问题清单

经全项目扫描，共发现 **10处** error日志不符合规范，涉及 **7个文件**：

### 1. `e.getMessage()` 作为占位符 + 异常对象重复传入（5处）

这类写法虽然Logback能正确识别，但 `e.getMessage()` 在消息体中冗余，且不符合规范格式。

| 文件 | 行号 | 当前代码 | 优化后 |
|------|------|----------|--------|
| `OpenAICompletion.java` | 60 | `log.error("> Completion \| 同步调用失败: {} <", e.getMessage(), e);` | `log.error("> Completion \| 同步调用失败 <", e);` |
| `OpenAICompletion.java` | 102 | `log.error("> Completion \| 流式调用失败: {} <", e.getMessage(), e);` | `log.error("> Completion \| 流式调用失败 <", e);` |
| `OpenAICompletion.java` | 142 | `log.error("> Completion \| 异步流式调用失败: {} <", e.getMessage(), e);` | `log.error("> Completion \| 异步流式调用失败 <", e);` |
| `OpenAILLMClient.java` | 38 | `log.error("> LLM \| 创建OpenAI客户端失败: {} <", e.getMessage(), e);` | `log.error("> LLM \| 创建OpenAI客户端失败 <", e);` |

### 2. 未传入异常对象，仅输出message（2处）

这类写法丢失了异常堆栈，不利于问题排查。

| 文件 | 行号 | 当前代码 | 优化后 |
|------|------|----------|--------|
| `ChatService.java` | 135 | `log.error("> ChatService \| SSE发送失败: {} <", e.getMessage());` | `log.error("> ChatService \| SSE发送失败 <", e);` |
| `ChatService.java` | 148 | `log.error("> ChatService \| 流式对话异常: {} <", e.getMessage());` | `log.error("> ChatService \| 流式对话异常 <", e);` |

### 3. 格式完全不符合 `> 模块 \| ... <` 规范（4处）

| 文件 | 行号 | 当前代码 | 优化后 |
|------|------|----------|--------|
| `AbstractTool.java` | 46 | `log.error("工具执行失败: {}, callId: {}, error: {}", definition.getName(), toolCall.getId(), e.getMessage(), e);` | `log.error("> Tool \| 工具执行失败: {} \| callId: {} <", definition.getName(), toolCall.getId(), e);` |
| `ToolExecutor.java` | 56 | `log.error("批量执行工具失败: {}", toolCall.getName(), e);` | `log.error("> ToolExecutor \| 批量执行工具失败: {} <", toolCall.getName(), e);` |
| `AnnotatedTool.java` | 63 | `log.error("注解工具执行失败: {}, callId: {}, error: {}", metadata.getDefinition().getName(), toolCall.getId(), cause.getMessage(), cause);` | `log.error("> Tool \| 注解工具执行失败: {} \| callId: {} <", metadata.getDefinition().getName(), toolCall.getId(), cause);` |
| `AnnotatedTool.java` | 68 | `log.error("注解工具执行失败: {}, callId: {}, error: {}", metadata.getDefinition().getName(), toolCall.getId(), e.getMessage(), e);` | `log.error("> Tool \| 注解工具执行失败: {} \| callId: {} <", metadata.getDefinition().getName(), toolCall.getId(), e);` |

## 符合规范无需修改的日志（6处）

以下error日志已符合规范，本次不涉及变更：

- `GlobalExceptionHandler.java:28`: `log.error("> GlobalExceptionHandler \| 系统异常 <", e);`
- `SimpleAgent.java:67`: `log.error("> Agent \| {} 同步调用失败 <", name, e);`
- `FileSystemPromptTemplateLoader.java:79`: `log.error("> Prompt \| 读取模板失败: {} <", templateName, e);`
- `ClasspathPromptTemplateLoader.java:82`: `log.error("> Prompt \| 读取Classpath模板失败: {} <", templateName, e);`
- `ReActAgent.java:72`: `log.error("> Agent \| {} ReAct同步调用失败 <", name, e);`
- `AbstractAgent.java:77`: `log.error("> Agent \| {} 异步流式调用失败 <", name, ex);`

## 变更范围

涉及模块与文件：
- `open-sagent-infrastructure-openai`: `OpenAICompletion.java`, `OpenAILLMClient.java`
- `open-sagent-web`: `ChatService.java`
- `open-sagent-core`: `AbstractTool.java`, `ToolExecutor.java`
- `open-sagent-infrastructure`: `AnnotatedTool.java`

## 影响分析

- **行为影响**：无。仅日志输出格式调整，不影响业务逻辑。
- **性能影响**：无。移除冗余的 `e.getMessage()` 调用，微优化。
- **排查体验**：正优化。统一格式便于日志采集和检索；补充缺失的异常堆栈输出。

## 评审记录

| 轮次 | 结果 | 意见 | 时间 |
|------|------|------|------|
| 1 | 通过 | 无 | 2026/04/28 |
