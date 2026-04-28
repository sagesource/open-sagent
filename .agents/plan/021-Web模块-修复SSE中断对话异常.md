# 方案：021-Web模块-修复SSE中断对话异常

## 1. 背景与目的

### 1.1 问题背景

当前 Web 模块的 SSE 流式对话接口，在客户端中断连接（如关闭浏览器页面、刷新页面、主动停止请求）时，后端会连续抛出多层异常：

1. `AsyncRequestNotUsableException`（Broken pipe）：客户端断开导致 `SseEmitter.send()` 失败
2. `IllegalStateException: ResponseBodyEmitter has already completed with error`：第一次异常后调用了 `emitter.completeWithError()`，但底层 LLM 流式回调仍在继续，后续 `send()` 触发该异常
3. `HttpMessageNotWritableException`：`GlobalExceptionHandler` 尝试返回 JSON，但 SSE 响应的 Content-Type 为 `text/event-stream`，无对应转换器

### 1.2 修复目的

- 将客户端断开连接视为**正常场景**，不再抛出 ERROR 级别异常
- 断开后停止向已失效的 `SseEmitter` 继续发送消息，避免异常连环爆发
- `GlobalExceptionHandler` 静默处理 `AsyncRequestNotUsableException`，避免错误日志污染

## 2. 修改方案

### 2.1 问题根因分析

```
客户端断开连接
    ↓
SseEmitter.send() 抛出 AsyncRequestNotUsableException（RuntimeException）
    ↓
ChatService catch(IOException e) 未捕获到（因为它是 RuntimeException）
    ↓
异常向上传播到 OpenAICompletion.executeStream 的 consumer.accept()
    ↓
while 循环中断，异常被 OpenAICompletion.stream() 包装为 OpenSagentLLMException
    ↓
ChatService 外层 catch 捕获，尝试 emitter.send(error) 再次失败
    ↓
completeWithError() 触发 emitter.onError()，后续回调继续 send 又抛 IllegalStateException
    ↓
GlobalExceptionHandler 捕获，尝试返回 JSON，Content-Type 不匹配又抛异常
```

### 2.2 修复策略

1. **ChatService**：引入 `AtomicBoolean emitterBroken` 标志，一旦检测到发送失败，标记断开并不再尝试后续发送；不调用 `emitter.completeWithError()`
2. **ChatService**：将 `catch (IOException)` 扩大为 `catch (Exception)`，以覆盖 `AsyncRequestNotUsableException` 等 RuntimeException
3. **GlobalExceptionHandler**：新增 `AsyncRequestNotUsableException` 处理器，静默返回（客户端断开是正常行为）

### 2.3 文件变更列表

| 文件路径 | 变更类型 | 说明 |
|----------|----------|------|
| `open-sagent-web/src/main/java/ai/sagesource/opensagent/web/service/ChatService.java` | 修改 | SSE 发送异常处理逻辑优化 |
| `open-sagent-web/src/main/java/ai/sagesource/opensagent/web/exception/GlobalExceptionHandler.java` | 修改 | 新增 AsyncRequestNotUsableException 静默处理 |

### 2.4 详细变更内容

#### 文件 1: ChatService.java

**变更点 1：增加 emitter 断开标志**

```java
public SseEmitter streamChat(Long userId, String sessionId, String message, String agentVersion) {
    // ... 原有逻辑不变 ...

    SseEmitter emitter = new SseEmitter(0L);
    String emitterId = sessionId + "-" + System.currentTimeMillis();
    // 新增：标记 SSE 连接是否已断开
    AtomicBoolean emitterBroken = new AtomicBoolean(false);

    // ... 原有逻辑不变 ...
```

**变更点 2：chunkConsumer 中增加断开检测和异常处理**

```java
Consumer<StreamChunk> chunkConsumer = chunk -> {
    // 新增：连接已断开则直接跳过，不再尝试发送
    if (emitterBroken.get()) {
        return;
    }
    try {
        if (chunk.getDeltaText() != null && !chunk.getDeltaText().isEmpty()) {
            if (chunk.getDeltaText().contains(ACTION_PREFIX)) {
                emitter.send(SseEmitter.event()
                        .name("action")
                        .data(chunk.getDeltaText()));
            } else {
                assistantResponse.append(chunk.getDeltaText());
                emitter.send(SseEmitter.event()
                        .name("message")
                        .data(chunk.getDeltaText()));
            }
        }
        if (chunk.isFinished()) {
            if (assistantResponse.length() > 0) {
                conversationService.saveMessage(
                        conversation.getId(),
                        "assistant",
                        assistantResponse.toString()
                );
            }
            if (finalNeedTitle) {
                String title = titleAgentService.generateTitle(message, messageCount <= 1);
                if (!"新对话".equals(title)) {
                    conversationService.updateTitle(userId, conversation.getId(), title);
                }
                emitter.send(SseEmitter.event()
                        .name("title")
                        .data(title));
            }
            emitter.send(SseEmitter.event()
                    .name("done")
                    .data(""));
            emitter.complete();
        }
    // 变更：扩大异常捕获范围，覆盖 AsyncRequestNotUsableException 等 RuntimeException
    } catch (Exception e) {
        // 变更：标记断开，降级为 warn（客户端断开是正常场景）
        emitterBroken.set(true);
        log.warn("> ChatService | SSE连接已断开，停止发送，sessionId: {} <", sessionId);
        // 变更：不再调用 completeWithError，避免触发后续 IllegalStateException
    }
};
```

**变更点 3：流式线程外层异常处理优化**

```java
new Thread(() -> {
    try {
        CompletionCancelToken token = agent.stream(
                UserCompletionMessage.of(message),
                chunkConsumer
        );
        activeTokens.put(emitterId, token);
    } catch (Exception e) {
        // 新增：如果连接已断开，无需再尝试发送错误事件
        if (emitterBroken.get()) {
            log.warn("> ChatService | 客户端已断开，忽略流式异常，sessionId: {} <", sessionId);
            return;
        }
        log.error("> ChatService | 流式对话异常 <", e);
        try {
            emitter.send(SseEmitter.event()
                    .name("error")
                    .data(e.getMessage()));
            emitter.complete();
        // 变更：扩大异常捕获范围
        } catch (Exception ex) {
            log.warn("> ChatService | SSE连接已断开，无法发送错误事件 <");
        }
    } finally {
        activeTokens.remove(emitterId);
    }
}).start();
```

#### 文件 2: GlobalExceptionHandler.java

**新增 AsyncRequestNotUsableException 处理器**

```java
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ... 原有 handleIllegalArgument 不变 ...

    /**
     * 新增：处理客户端断开连接的 SSE 异常
     * <p>
     * AsyncRequestNotUsableException 是客户端正常断开 SSE 连接时抛出的异常，
     * 属于预期内的正常场景，无需记录 ERROR 日志，也无需返回响应（连接已断开）。
     */
    @ExceptionHandler(org.springframework.web.context.request.async.AsyncRequestNotUsableException.class)
    public void handleAsyncRequestNotUsable(org.springframework.web.context.request.async.AsyncRequestNotUsableException e) {
        log.debug("> GlobalExceptionHandler | 客户端SSE连接已断开 <");
    }

    // ... 原有 handleException 不变 ...
}
```

## 3. 影响范围分析

| 影响项 | 影响程度 | 说明 |
|--------|----------|------|
| SSE 流式对话接口 | 低 | 仅优化异常处理逻辑，业务行为不变 |
| 全局异常处理 | 低 | 新增对 AsyncRequestNotUsableException 的静默处理 |
| 客户端体验 | 无 | 前端无需任何改动 |
| 日志输出 | 中 | 客户端断开场景从 ERROR 降级为 WARN/DEBUG，减少日志污染 |
| LLM Token 消耗 | 无 | 当前架构下（同步 stream），客户端断开后 LLM 流仍会继续到结束。这是已知限制，不影响本次 bug 修复 |

## 4. 测试计划

1. **正常对话测试**：发起 SSE 流式对话，确认消息正常接收、done 事件正常触发
2. **客户端断开测试**：
   - 在对话过程中关闭浏览器页面，观察后端日志
   - 预期：仅输出一条 WARN 级别的 "SSE连接已断开" 日志，无 ERROR 异常堆栈
3. **刷新页面测试**：对话过程中刷新页面，验证无连环异常
4. **中断按钮测试**：点击前端中断按钮后，验证无异常抛出
5. **异常对话测试**：模拟 LLM 调用失败场景，确认 error 事件仍能正常发送给客户端

## 5. 评审记录

| 评审人 | 时间 | 结论 | 备注 |
|--------|------|------|------|
| sage.xue | 2026-04-28 | 通过 | |
