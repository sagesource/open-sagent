# 方案：018-Web模块-修复chat_message表未记录assistant消息

## 1. 背景与目的

### 1.1 背景

在Web模块的SSE流式对话流程中，`ChatService.streamChat()` 方法在接收到用户消息后，会调用 `conversationService.saveMessage()` 将用户消息持久化到 `chat_message` 表（role="user"）。然而，模型返回的完整 assistant 响应内容仅在流式过程中通过 SSE 推送给前端，**并未持久化到 `chat_message` 表**。

这导致以下问题：
- 前端调用 `/api/conversations/{id}/messages` 接口获取消息历史时，只能查询到 user 发送的消息，assistant 的回复消息缺失
- 页面刷新或重新加载对话后，消息列表中只有用户的问题，没有模型的回答

### 1.2 目的

修复 `ChatService.streamChat()` 方法，在流式对话结束时将完整的 assistant 响应内容保存到 `chat_message` 表，确保消息历史完整性。

## 2. 修改方案

### 2.1 文件变更列表

| 文件路径 | 变更类型 | 说明 |
|----------|----------|------|
| `open-sagent-web/src/main/java/ai/sagesource/opensagent/web/service/ChatService.java` | 修改 | 在流式对话结束时保存 assistant 消息到 chat_message 表 |

### 2.2 详细变更内容

#### 文件：`ChatService.java`

**修改思路**：
在 `streamChat()` 方法中声明一个 `StringBuilder` 用于收集 assistant 的完整响应文本。在 `chunkConsumer` 处理每个 `StreamChunk` 时，将非 ACTION 标记的 `deltaText` 追加到 `StringBuilder` 中。在流式结束（`chunk.isFinished()`）时，调用 `conversationService.saveMessage()` 保存 role="assistant" 的完整消息。

**关键代码变更**：

```java
public SseEmitter streamChat(Long userId, String sessionId, String message, String agentVersion) {
    // ... 前置校验和初始化不变 ...

    conversationService.saveMessage(conversation.getId(), "user", message);

    // ... needTitle 判断和 Memory 创建不变 ...

    final boolean finalNeedTitle = needTitle;
    // 新增：用于收集 assistant 完整响应
    final StringBuilder assistantResponse = new StringBuilder();

    Consumer<StreamChunk> chunkConsumer = chunk -> {
        try {
            if (chunk.getDeltaText() != null && !chunk.getDeltaText().isEmpty()) {
                if (chunk.getDeltaText().contains(ACTION_PREFIX)) {
                    emitter.send(SseEmitter.event()
                            .name("action")
                            .data(chunk.getDeltaText()));
                } else {
                    // 新增：收集 assistant 响应文本
                    assistantResponse.append(chunk.getDeltaText());
                    emitter.send(SseEmitter.event()
                            .name("message")
                            .data(chunk.getDeltaText()));
                }
            }
            if (chunk.isFinished()) {
                // 新增：保存 assistant 完整响应到 chat_message 表
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
        } catch (IOException e) {
            log.error("> ChatService | SSE发送失败 <", e);
            emitter.completeWithError(e);
        }
    };

    // ... 后续线程执行和 emitter 回调不变 ...
}
```

## 3. 影响范围分析

| 范围 | 影响说明 |
|------|----------|
| `ChatService.streamChat()` | 仅新增 assistant 消息保存逻辑，不影响现有流式输出、标题生成、异常处理等流程 |
| `chat_message` 表 | 新增 assistant 角色的消息记录，与 user 消息保持一致的存储结构 |
| 前端消息历史加载 | 修复后 `/api/conversations/{id}/messages` 返回的消息列表将包含完整的 user + assistant 对话 |
| 其他模块 | 无影响，修改范围仅限于 `ChatService` 单文件 |

## 4. 测试计划

1. **单元测试验证**：
   - 发送一条消息，验证 `chat_message` 表中同时存在 role="user" 和 role="assistant" 两条记录
   - 验证 assistant 消息的内容与模型返回的完整响应一致

2. **页面验证**：
   - 刷新对话页面，验证消息历史列表中同时显示用户问题和模型回答
   - 多轮对话后刷新页面，验证历史消息顺序和内容完整

3. **边界情况**：
   - 模型返回空响应时，不插入 assistant 记录（避免空消息）
   - 流式过程中发生异常时，已接收的部分内容是否保存（当前方案：异常时不会走到 `isFinished()`，因此不保存；与现有行为一致）

## 5. 评审记录

| 评审人 | 时间 | 结论 | 备注 |
|--------|------|------|------|
| sage | 2026-04-28 | 通过 | 方案编号已修正为018 |
