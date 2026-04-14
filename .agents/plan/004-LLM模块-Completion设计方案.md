# 方案：004-LLM模块-Completion设计方案

## 1. 背景与目的

### 1.1 背景

Open Sagent框架已完成LLM模块的Client（001）、Message（002）、Tool（003）三个子模块的设计与实现。根据项目架构方案 `project_design_llm.md`，Completion模块是LLM交互的核心，负责封装与大模型的对话补全能力，包括同步、异步、流式调用以及工具调用（Tool-Calling）功能。

### 1.2 目的

1. 在Core模块定义统一的Completion抽象接口和数据模型（请求、响应、流式分片、取消令牌）
2. 在Infrastructure模块提供基于OpenAI SDK的Completion具体实现
3. 支持同步调用、异步调用、同步流式调用、异步流式调用四种调用模式
4. 支持Tool-Calling功能，非流式场景直接返回ToolCall列表
5. 在流式调用场景下，Infrastructure层负责增量聚合大模型返回的ToolCall信息，并在流结束时转换为完整的Messages.toolCalls
6. 流式和异步调用支持中断/取消机制

## 2. 修改方案

### 2.1 模块职责边界

```
open-sagent-core (抽象定义层)
    ├── CompletionRequest         补全请求参数
    ├── CompletionResponse        补全响应结果
    ├── TokenUsage                Token用量统计
    ├── StreamChunk               流式分片数据
    ├── CompletionCancelToken     取消令牌接口
    ├── LLMCompletion             Completion抽象接口
    └── CompletionUtils           便捷工具方法

open-sagent-infrastructure (具体实现层)
    ├── OpenAICompletion          OpenAI Completion实现
    ├── OpenAICompletionFactory   工厂类
    └── OpenAIToolCallAggregator  流式ToolCall增量聚合器（内部类）
```

### 2.2 文件变更列表

#### open-sagent-core

| 文件路径 | 变更类型 | 说明 |
|----------|----------|------|
| `.../core/llm/completion/CompletionRequest.java` | 新增 | 补全请求参数 |
| `.../core/llm/completion/CompletionResponse.java` | 新增 | 补全响应结果 |
| `.../core/llm/completion/TokenUsage.java` | 新增 | Token用量统计 |
| `.../core/llm/completion/StreamChunk.java` | 新增 | 流式分片数据 |
| `.../core/llm/completion/CompletionCancelToken.java` | 新增 | 取消令牌接口 |
| `.../core/llm/completion/LLMCompletion.java` | 新增 | Completion抽象接口 |
| `.../core/llm/completion/CompletionUtils.java` | 新增 | 便捷工具方法 |

#### open-sagent-infrastructure

| 文件路径 | 变更类型 | 说明 |
|----------|----------|------|
| `.../infrastructure/llm/openai/OpenAICompletion.java` | 新增 | OpenAI Completion实现 |
| `.../infrastructure/llm/openai/OpenAICompletionFactory.java` | 新增 | OpenAI Completion工厂 |

### 2.3 详细变更内容

#### 文件 1: `open-sagent-core/.../llm/completion/CompletionRequest.java`

```java
package ai.sagesource.opensagent.core.llm.completion;

import ai.sagesource.opensagent.core.llm.message.CompletionMessage;
import ai.sagesource.opensagent.core.llm.tool.ToolDefinition;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 补全请求参数
 * <p>
 * 封装调用大模型进行对话补全所需的请求参数
 *
 * @author: sage.xue
 * @time: 2026/4/14
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompletionRequest {

    /**
     * 对话消息列表
     */
    @Builder.Default
    private List<CompletionMessage> messages = new ArrayList<>();

    /**
     * 工具定义列表（可选）
     */
    @Builder.Default
    private List<ToolDefinition> tools = new ArrayList<>();

    /**
     * 采样温度（0-2）
     */
    private Double temperature;

    /**
     * 最大生成Token数
     */
    private Integer maxTokens;

    /**
     * 是否流式输出
     */
    @Builder.Default
    private boolean stream = false;

    /**
     * 添加消息（便捷方法）
     *
     * @param message 消息
     */
    public void addMessage(CompletionMessage message) {
        if (this.messages == null) {
            this.messages = new ArrayList<>();
        }
        this.messages.add(message);
    }

    /**
     * 添加工具定义（便捷方法）
     *
     * @param tool 工具定义
     */
    public void addTool(ToolDefinition tool) {
        if (this.tools == null) {
            this.tools = new ArrayList<>();
        }
        this.tools.add(tool);
    }
}
```

#### 文件 2: `open-sagent-core/.../llm/completion/TokenUsage.java`

```java
package ai.sagesource.opensagent.core.llm.completion;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Token用量统计
 *
 * @author: sage.xue
 * @time: 2026/4/14
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TokenUsage {

    /**
     * 输入Token数
     */
    private Integer promptTokens;

    /**
     * 输出Token数
     */
    private Integer completionTokens;

    /**
     * 总Token数
     */
    private Integer totalTokens;
}
```

#### 文件 3: `open-sagent-core/.../llm/completion/CompletionResponse.java`

```java
package ai.sagesource.opensagent.core.llm.completion;

import ai.sagesource.opensagent.core.llm.message.AssistantCompletionMessage;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 补全响应结果
 * <p>
 * 封装大模型返回的对话补全结果
 *
 * @author: sage.xue
 * @time: 2026/4/14
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompletionResponse {

    /**
     * 响应ID
     */
    private String responseId;

    /**
     * 模型名称
     */
    private String model;

    /**
     * 助手的回复消息
     */
    private AssistantCompletionMessage message;

    /**
     * 结束原因
     */
    private String finishReason;

    /**
     * Token用量统计
     */
    private TokenUsage usage;

    /**
     * 是否包含工具调用请求
     */
    public boolean hasToolCalls() {
        return message != null
                && message.getToolCalls() != null
                && !message.getToolCalls().isEmpty();
    }
}
```

#### 文件 4: `open-sagent-core/.../llm/completion/StreamChunk.java`

```java
package ai.sagesource.opensagent.core.llm.completion;

import ai.sagesource.opensagent.core.llm.tool.ToolCall;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 流式分片数据
 * <p>
 * 封装流式调用过程中每次返回的增量数据
 *
 * @author: sage.xue
 * @time: 2026/4/14
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StreamChunk {

    /**
     * 增量文本内容
     */
    private String deltaText;

    /**
     * 增量推理内容
     */
    private String deltaReasoningText;

    /**
     * 本次chunk中新增/更新的工具调用列表（增量）
     */
    @Builder.Default
    private List<ToolCall> deltaToolCalls = new ArrayList<>();

    /**
     * 当前已聚合的完整文本（可选，由实现层决定）
     */
    private String aggregatedText;

    /**
     * 结束原因（仅在流结束时提供）
     */
    private String finishReason;

    /**
     * 是否已结束
     */
    @Builder.Default
    private boolean finished = false;

    /**
     * 是否包含有效增量数据
     */
    public boolean hasDelta() {
        return (deltaText != null && !deltaText.isEmpty())
                || (deltaReasoningText != null && !deltaReasoningText.isEmpty())
                || (deltaToolCalls != null && !deltaToolCalls.isEmpty());
    }
}
```

#### 文件 5: `open-sagent-core/.../llm/completion/CompletionCancelToken.java`

```java
package ai.sagesource.opensagent.core.llm.completion;

/**
 * 取消令牌接口
 * <p>
 * 用于在流式或异步调用过程中请求中断
 *
 * @author: sage.xue
 * @time: 2026/4/14
 */
public interface CompletionCancelToken {

    /**
     * 请求取消当前调用
     */
    void cancel();

    /**
     * 是否已取消
     *
     * @return true表示已取消
     */
    boolean isCancelled();
}
```

#### 文件 6: `open-sagent-core/.../llm/completion/LLMCompletion.java`

```java
package ai.sagesource.opensagent.core.llm.completion;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * Completion抽象接口
 * <p>
 * 定义与大模型进行对话补全的统一接口，支持同步、异步、流式四种调用模式
 *
 * @author: sage.xue
 * @time: 2026/4/14
 */
public interface LLMCompletion {

    /**
     * 同步调用
     *
     * @param request 补全请求
     * @return 补全响应
     */
    CompletionResponse complete(CompletionRequest request);

    /**
     * 异步调用
     *
     * @param request  补全请求
     * @param executor 执行器（由调用方传入）
     * @return 异步补全响应
     */
    CompletableFuture<CompletionResponse> completeAsync(CompletionRequest request, Executor executor);

    /**
     * 同步流式调用
     * <p>
     * 通过Consumer逐块接收流式数据，返回取消令牌用于中断
     *
     * @param request  补全请求
     * @param consumer 流式分片消费者
     * @return 取消令牌
     */
    CompletionCancelToken stream(CompletionRequest request, Consumer<StreamChunk> consumer);

    /**
     * 异步流式调用
     *
     * @param request  补全请求
     * @param consumer 流式分片消费者
     * @param executor 执行器（由调用方传入）
     * @return 取消令牌
     */
    CompletionCancelToken streamAsync(CompletionRequest request, Consumer<StreamChunk> consumer, Executor executor);
}
```

#### 文件 7: `open-sagent-core/.../llm/completion/CompletionUtils.java`

```java
package ai.sagesource.opensagent.core.llm.completion;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Completion便捷工具方法
 *
 * @author: sage.xue
 * @time: 2026/4/14
 */
public final class CompletionUtils {

    private CompletionUtils() {
        // 工具类禁止实例化
    }

    /**
     * 将流式调用结果聚合为完整的CompletionResponse
     * <p>
     * 仅供辅助使用，聚合后的response不包含usage和finishReason等元数据（取决于实现层支持）
     *
     * @param completion Completion实现
     * @param request    请求参数
     * @return 聚合后的响应
     */
    public static CompletionResponse aggregateStream(LLMCompletion completion, CompletionRequest request) {
        StringBuilder textBuilder = new StringBuilder();
        StringBuilder reasoningBuilder = new StringBuilder();
        List<ai.sagesource.opensagent.core.llm.tool.ToolCall> toolCalls = new ArrayList<>();
        final String[] finishReason = new String[1];

        CompletionCancelToken token = completion.stream(request, chunk -> {
            if (chunk.isFinished()) {
                finishReason[0] = chunk.getFinishReason();
                return;
            }
            if (chunk.getDeltaText() != null) {
                textBuilder.append(chunk.getDeltaText());
            }
            if (chunk.getDeltaReasoningText() != null) {
                reasoningBuilder.append(chunk.getDeltaReasoningText());
            }
            if (chunk.getDeltaToolCalls() != null && !chunk.getDeltaToolCalls().isEmpty()) {
                // 流式ToolCall由实现层在最终chunk中提供完整列表
                toolCalls.addAll(chunk.getDeltaToolCalls());
            }
        });

        // stream是同步阻塞的，执行到这里说明流已结束或已取消
        return CompletionResponse.builder()
                .message(ai.sagesource.opensagent.core.llm.message.AssistantCompletionMessage.builder()
                        .contents(new ArrayList<>(List.of(
                                ai.sagesource.opensagent.core.llm.message.TextContent.builder()
                                        .text(textBuilder.toString()).build())))
                        .reasoningContent(reasoningBuilder.length() > 0 ? reasoningBuilder.toString() : null)
                        .toolCalls(toolCalls)
                        .build())
                .finishReason(finishReason[0])
                .build();
    }
}
```

#### 文件 8: `open-sagent-infrastructure/.../llm/openai/OpenAICompletion.java`

```java
package ai.sagesource.opensagent.infrastructure.llm.openai;

import ai.sagesource.opensagent.core.llm.completion.*;
import ai.sagesource.opensagent.core.llm.exception.OpenSagentLLMException;
import ai.sagesource.opensagent.core.llm.message.*;
import ai.sagesource.opensagent.core.llm.tool.ToolCall;
import ai.sagesource.opensagent.core.llm.tool.ToolDefinition;
import com.openai.client.OpenAIClient;
import com.openai.models.chat.completions.*;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * OpenAI Completion实现
 * <p>
 * 基于OpenAI SDK实现LLMCompletion接口
 *
 * @author: sage.xue
 * @time: 2026/4/14
 */
@Slf4j
public class OpenAICompletion implements LLMCompletion {

    private final OpenAIClient client;
    private final String model;

    public OpenAICompletion(OpenAIClient client, String model) {
        if (client == null) {
            throw new OpenSagentLLMException("OpenAIClient不能为空");
        }
        if (model == null || model.isEmpty()) {
            throw new OpenSagentLLMException("模型名称不能为空");
        }
        this.client = client;
        this.model = model;
    }

    @Override
    public CompletionResponse complete(CompletionRequest request) {
        try {
            log.info("> Completion | 开始同步调用，模型: {} <", model);
            ChatCompletionCreateParams params = buildParams(request, false);
            ChatCompletion completion = client.chat().completions().create(params);
            CompletionResponse response = mapResponse(completion);
            log.info("> Completion | 同步调用完成，finishReason: {} <", response.getFinishReason());
            return response;
        } catch (Exception e) {
            log.error("> Completion | 同步调用失败: {} <", e.getMessage(), e);
            throw new OpenSagentLLMException("OpenAI Completion调用失败: " + e.getMessage(), e);
        }
    }

    @Override
    public CompletableFuture<CompletionResponse> completeAsync(CompletionRequest request, Executor executor) {
        if (executor == null) {
            throw new OpenSagentLLMException("Executor不能为空");
        }
        return CompletableFuture.supplyAsync(() -> complete(request), executor);
    }

    @Override
    public CompletionCancelToken stream(CompletionRequest request, Consumer<StreamChunk> consumer) {
        AtomicBoolean cancelled = new AtomicBoolean(false);
        CompletionCancelToken token = new CompletionCancelToken() {
            @Override
            public void cancel() {
                cancelled.set(true);
            }

            @Override
            public boolean isCancelled() {
                return cancelled.get();
            }
        };

        try {
            log.info("> Completion | 开始流式调用，模型: {} <", model);
            ChatCompletionCreateParams params = buildParams(request, true);

            // OpenAI SDK流式返回
            com.openai.core.http.StreamResponse<ChatCompletionChunk> streamResponse =
                    client.chat().completions().createStreaming(params);

            ChatCompletionAccumulator accumulator = ChatCompletionAccumulator.create();
            StringBuilder textBuilder = new StringBuilder();
            StringBuilder reasoningBuilder = new StringBuilder();
            String finishReason = null;

            for (ChatCompletionChunk chunk : streamResponse) {
                if (cancelled.get()) {
                    log.warn("> Completion | 流式调用被取消 <");
                    break;
                }

                // 使用SDK提供的Accumulator进行聚合
                accumulator.accumulate(chunk);

                ChatCompletionChunk.Choice choice = chunk.choices().isEmpty()
                        ? null : chunk.choices().get(0);
                if (choice == null) {
                    continue;
                }

                ChatCompletionChunk.Choice.Delta delta = choice.delta();
                if (choice.finishReason().isPresent()) {
                    finishReason = choice.finishReason().get().asString();
                }

                // 处理文本增量
                String deltaText = extractDeltaText(delta);
                if (deltaText != null && !deltaText.isEmpty()) {
                    textBuilder.append(deltaText);
                }

                // 处理推理增量（OpenAI o1系列可能支持）
                String deltaReasoning = extractDeltaReasoning(delta);
                if (deltaReasoning != null && !deltaReasoning.isEmpty()) {
                    reasoningBuilder.append(deltaReasoning);
                }

                // 处理ToolCall增量：从accumulator当前状态获取本次新增/更新的ToolCall
                List<ToolCall> deltaToolCalls = extractDeltaToolCalls(accumulator, chunk);

                StreamChunk streamChunk = StreamChunk.builder()
                        .deltaText(deltaText)
                        .deltaReasoningText(deltaReasoning)
                        .deltaToolCalls(deltaToolCalls)
                        .aggregatedText(textBuilder.toString())
                        .finishReason(finishReason)
                        .build();

                if (streamChunk.hasDelta() || finishReason != null) {
                    consumer.accept(streamChunk);
                }

                // 流结束
                if (finishReason != null) {
                    // 通过accumulator获取完整的聚合结果，提取最终ToolCall列表
                    ChatCompletion accumulated = accumulator.chatCompletion();
                    CompletionResponse finalResponse = mapResponse(accumulated);
                    List<ToolCall> finalToolCalls = finalResponse.getMessage() != null
                            ? finalResponse.getMessage().getToolCalls() : new ArrayList<>();
                    if (!finalToolCalls.isEmpty()) {
                        consumer.accept(StreamChunk.builder()
                                .deltaToolCalls(finalToolCalls)
                                .aggregatedText(textBuilder.toString())
                                .finishReason(finishReason)
                                .build());
                    }
                    consumer.accept(StreamChunk.builder()
                            .finished(true)
                            .finishReason(finishReason)
                            .aggregatedText(textBuilder.toString())
                            .build());
                    break;
                }
            }

            log.info("> Completion | 流式调用结束 <");
        } catch (Exception e) {
            log.error("> Completion | 流式调用失败: {} <", e.getMessage(), e);
            throw new OpenSagentLLMException("OpenAI Completion流式调用失败: " + e.getMessage(), e);
        }

        return token;
    }

    @Override
    public CompletionCancelToken streamAsync(CompletionRequest request, Consumer<StreamChunk> consumer, Executor executor) {
        if (executor == null) {
            throw new OpenSagentLLMException("Executor不能为空");
        }
        AtomicBoolean cancelled = new AtomicBoolean(false);
        CompletionCancelToken token = new CompletionCancelToken() {
            @Override
            public void cancel() {
                cancelled.set(true);
            }

            @Override
            public boolean isCancelled() {
                return cancelled.get();
            }
        };

        CompletableFuture.runAsync(() -> stream(request, consumer), executor);
        return token;
    }

    // ========== 私有辅助方法 ==========

    private ChatCompletionCreateParams buildParams(CompletionRequest request, boolean stream) {
        ChatCompletionCreateParams.Builder builder = ChatCompletionCreateParams.builder()
                .model(model)
                .stream(stream);

        // 转换消息
        if (request.getMessages() != null) {
            for (CompletionMessage msg : request.getMessages()) {
                builder.addMessage(convertMessage(msg));
            }
        }

        // 转换工具
        if (request.getTools() != null && !request.getTools().isEmpty()) {
            for (ToolDefinition tool : request.getTools()) {
                builder.addTool(convertTool(tool));
            }
        }

        if (request.getTemperature() != null) {
            builder.temperature(request.getTemperature());
        }
        if (request.getMaxTokens() != null) {
            builder.maxTokens(request.getMaxTokens());
        }

        return builder.build();
    }

    private ChatCompletionMessageParam convertMessage(CompletionMessage msg) {
        switch (msg.getRole()) {
            case SYSTEM -> {
                return ChatCompletionMessageParam.ofSystem(
                        ChatCompletionSystemMessageParam.builder()
                                .content(msg.getTextContent())
                                .build());
            }
            case USER -> {
                return ChatCompletionMessageParam.ofUser(
                        ChatCompletionUserMessageParam.builder()
                                .content(msg.getTextContent())
                                .build());
            }
            case ASSISTANT -> {
                ChatCompletionAssistantMessageParam.Builder assistantBuilder =
                        ChatCompletionAssistantMessageParam.builder()
                                .content(msg.getTextContent());
                return ChatCompletionMessageParam.ofAssistant(assistantBuilder.build());
            }
            case TOOL -> {
                if (msg instanceof ToolCompletionMessage toolMsg) {
                    return ChatCompletionMessageParam.ofTool(
                            ChatCompletionToolMessageParam.builder()
                                    .content(toolMsg.getTextContent())
                                    .toolCallId(toolMsg.getToolCallId())
                                    .build());
                }
                return ChatCompletionMessageParam.ofTool(
                        ChatCompletionToolMessageParam.builder()
                                .content(msg.getTextContent())
                                .build());
            }
            default -> {
                return ChatCompletionMessageParam.ofUser(
                        ChatCompletionUserMessageParam.builder()
                                .content(msg.getTextContent())
                                .build());
            }
        }
    }

    private ChatCompletionTool convertTool(ToolDefinition tool) {
        return ChatCompletionTool.builder()
                .function(FunctionDefinition.builder()
                        .name(tool.getName())
                        .description(tool.getDescription())
                        .parameters(FunctionDefinition.Parameters.of(tool.toParameterSchema()))
                        .build())
                .build();
    }

    private CompletionResponse mapResponse(ChatCompletion completion) {
        if (completion.choices().isEmpty()) {
            throw new OpenSagentLLMException("OpenAI返回空choices");
        }
        ChatCompletion.Choice choice = completion.choices().get(0);
        ChatCompletionMessage message = choice.message();

        AssistantCompletionMessage assistantMsg = AssistantCompletionMessage.builder()
                .messageId(completion.id())
                .contents(new ArrayList<>(List.of(
                        TextContent.builder().text(message.content().orElse("")).build())))
                .build();

        // 解析ToolCalls
        if (message.toolCalls().isPresent() && !message.toolCalls().get().isEmpty()) {
            List<ToolCall> toolCalls = new ArrayList<>();
            for (ChatCompletionMessageToolCall tc : message.toolCalls().get()) {
                toolCalls.add(ToolCall.builder()
                        .id(tc.id())
                        .name(tc.function().name())
                        .arguments(parseArguments(tc.function().arguments()))
                        .build());
            }
            assistantMsg.setToolCalls(toolCalls);
        }

        TokenUsage usage = null;
        if (completion.usage().isPresent()) {
            CompletionUsage u = completion.usage().get();
            usage = TokenUsage.builder()
                    .promptTokens(u.promptTokens())
                    .completionTokens(u.completionTokens())
                    .totalTokens(u.totalTokens())
                    .build();
        }

        return CompletionResponse.builder()
                .responseId(completion.id())
                .model(completion.model())
                .message(assistantMsg)
                .finishReason(choice.finishReason().isPresent()
                        ? choice.finishReason().get().asString() : null)
                .usage(usage)
                .build();
    }

    private String extractDeltaText(ChatCompletionChunk.Choice.Delta delta) {
        if (delta == null) {
            return null;
        }
        if (delta.content().isPresent()) {
            return delta.content().get();
        }
        return null;
    }

    private String extractDeltaReasoning(ChatCompletionChunk.Choice.Delta delta) {
        // OpenAI SDK 4.26.0 中，reasoning内容可能通过其他字段暴露，当前版本先返回null
        return null;
    }

    /**
     * 从accumulator当前聚合状态和当前chunk中提取增量的ToolCall
     */
    private List<ToolCall> extractDeltaToolCalls(ChatCompletionAccumulator accumulator, ChatCompletionChunk chunk) {
        List<ToolCall> result = new ArrayList<>();
        ChatCompletion accumulated = accumulator.chatCompletion();
        if (accumulated.choices().isEmpty()) {
            return result;
        }
        ChatCompletionMessage message = accumulated.choices().get(0).message();
        if (message.toolCalls().isEmpty()) {
            return result;
        }
        // 当前chunk中的tool call索引
        Set<Integer> indices = new HashSet<>();
        ChatCompletionChunk.Choice choice = chunk.choices().isEmpty() ? null : chunk.choices().get(0);
        if (choice != null && choice.delta() != null && choice.delta().toolCalls().isPresent()) {
            for (ChatCompletionChunk.Choice.Delta.ToolCall dt : choice.delta().toolCalls().get()) {
                indices.add(dt.index());
            }
        }
        List<ChatCompletionMessageToolCall> all = message.toolCalls().get();
        for (int index : indices) {
            if (index >= 0 && index < all.size()) {
                ChatCompletionMessageToolCall tc = all.get(index);
                result.add(ToolCall.builder()
                        .id(tc.id())
                        .name(tc.function().name())
                        .arguments(parseArguments(tc.function().arguments()))
                        .build());
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseArguments(String json) {
        if (json == null || json.isEmpty()) {
            return new LinkedHashMap<>();
        }
        try {
            com.alibaba.fastjson2.JSONObject obj = com.alibaba.fastjson2.JSON.parseObject(json);
            return obj.getInnerMap();
        } catch (Exception e) {
            log.warn("> Completion | 解析Tool参数失败: {} <", json);
            return new LinkedHashMap<>();
        }
    }
}
```

#### 文件 9: `open-sagent-infrastructure/.../llm/openai/OpenAICompletionFactory.java`

```java
package ai.sagesource.opensagent.infrastructure.llm.openai;

import ai.sagesource.opensagent.core.llm.client.LLMClient;
import ai.sagesource.opensagent.core.llm.completion.LLMCompletion;
import ai.sagesource.opensagent.core.llm.exception.OpenSagentLLMException;

/**
 * OpenAI Completion工厂类
 *
 * @author: sage.xue
 * @time: 2026/4/14
 */
public class OpenAICompletionFactory {

    /**
     * 基于LLMClient创建Completion实例
     *
     * @param client LLMClient（必须是OpenAILLMClient实例）
     * @return LLMCompletion实例
     * @throws OpenSagentLLMException 当client类型不匹配时抛出
     */
    public static LLMCompletion createCompletion(LLMClient client) {
        if (client == null) {
            throw new OpenSagentLLMException("LLMClient不能为空");
        }
        if (!(client instanceof OpenAILLMClient openAIClient)) {
            throw new OpenSagentLLMException("LLMClient必须是OpenAILLMClient类型");
        }
        return new OpenAICompletion(openAIClient.getOpenAIClient(), openAIClient.getConfig().getModel());
    }

    /**
     * 直接基于OpenAILLMClient创建Completion实例
     *
     * @param client OpenAI客户端
     * @return LLMCompletion实例
     */
    public static LLMCompletion createCompletion(OpenAILLMClient client) {
        if (client == null) {
            throw new OpenSagentLLMException("OpenAILLMClient不能为空");
        }
        return new OpenAICompletion(client.getOpenAIClient(), client.getConfig().getModel());
    }
}
```

## 3. 影响范围分析

### 3.1 模块依赖关系

```
open-sagent-core (抽象定义层)
    ├── CompletionRequest / CompletionResponse / TokenUsage
    ├── StreamChunk / CompletionCancelToken
    ├── LLMCompletion (接口)
    └── CompletionUtils

open-sagent-infrastructure (具体实现层)
    ├── 依赖 open-sagent-core
    ├── OpenAICompletion (实现)
    └── OpenAICompletionFactory (工厂)
```

### 3.2 影响范围

| 模块 | 影响说明 |
|------|----------|
| open-sagent-core | 新增Completion模块核心抽象，无破坏性变更 |
| open-sagent-infrastructure | 新增OpenAI Completion实现，依赖已有的OpenAILLMClient |
| open-sagent-tools | 无直接影响，后续Agent可通过LLMCompletion调用大模型 |
| open-sagent-web/cli/example | 无直接影响 |

### 3.3 扩展性说明

1. **支持新增厂商**：通过实现`LLMCompletion`接口并在Infrastructure模块添加对应的工厂类（如AnthropicCompletionFactory），可支持多厂商接入
2. **调用模式扩展**：四种调用模式定义在接口层，新增厂商需全部实现
3. **流式ToolCall聚合**：OpenAI实现中使用SDK自带的`ChatCompletionAccumulator`进行聚合，流结束时通过聚合结果获取完整的ToolCall列表
4. **消息转换**：`convertMessage`方法目前优先处理文本内容，多模态内容（图片、文件）的转换将在后续迭代中扩展

## 4. 测试计划

### 4.1 单元测试

| 测试类 | 模块 | 测试内容 |
|--------|------|----------|
| `CompletionRequestTest` | core | 请求参数构建、消息/工具添加 |
| `CompletionResponseTest` | core | 响应构建、hasToolCalls判断 |
| `StreamChunkTest` | core | 分片构建、hasDelta判断 |
| `OpenAICompletionFactoryTest` | infrastructure | 工厂参数校验、类型校验 |

### 4.2 集成测试

| 测试类 | 模块 | 测试内容 |
|--------|------|----------|
| `OpenAICompletionIntegrationTest` | infrastructure | 真实API同步调用、流式调用、Tool-Calling |

### 4.3 编译验证

```bash
mvn clean compile test-compile -pl open-sagent-infrastructure -am
```

## 5. 方案变更记录

### 变更 1（2026-04-14）：流式ToolCall聚合改用ChatCompletionAccumulator

**变更原因：**
1. 用户评审指出：OpenAI Java SDK已提供`ChatCompletionAccumulator`用于流式响应聚合，无需手写`ToolCallAggregator`
2. 使用SDK内置聚合器可降低维护成本，且行为与官方保持一致

**文件变更：**

| 文件路径 | 变更类型 | 说明 |
|----------|----------|------|
| `.../infrastructure/llm/openai/OpenAICompletion.java` | 修改 | 移除手写的`ToolCallAggregator`内部类；`stream`方法中引入`ChatCompletionAccumulator`进行增量聚合；新增`extractDeltaToolCalls`辅助方法 |

**关键代码变更：**
```java
// 修改前：手写聚合器
private static class ToolCallAggregator { ... }

// 修改后：使用SDK内置ChatCompletionAccumulator
ChatCompletionAccumulator accumulator = ChatCompletionAccumulator.create();
for (ChatCompletionChunk chunk : streamResponse) {
    accumulator.accumulate(chunk);
    // ...
}
// 流结束时通过accumulator.chatCompletion()获取完整结果
ChatCompletion accumulated = accumulator.chatCompletion();
CompletionResponse finalResponse = mapResponse(accumulated);
```

### 变更 2（2026-04-14）：异步方法要求调用方传入Executor

**变更原因：**
1. 用户评审指出：异步执行时，Executor应由调用方传入，避免使用默认的ForkJoinPool
2. 便于调用方控制线程资源、异常处理和执行策略

**文件变更：**

| 文件路径 | 变更类型 | 说明 |
|----------|----------|------|
| `.../core/llm/completion/LLMCompletion.java` | 修改 | `completeAsync`和`streamAsync`方法新增`Executor`参数 |
| `.../infrastructure/llm/openai/OpenAICompletion.java` | 修改 | 实现新增参数，空参数时抛出`OpenSagentLLMException`；`CompletableFuture.supplyAsync/runAsync`使用传入的executor |

**关键代码变更：**
```java
// 修改前
CompletableFuture<CompletionResponse> completeAsync(CompletionRequest request);
CompletionCancelToken streamAsync(CompletionRequest request, Consumer<StreamChunk> consumer);

// 修改后
CompletableFuture<CompletionResponse> completeAsync(CompletionRequest request, Executor executor);
CompletionCancelToken streamAsync(CompletionRequest request, Consumer<StreamChunk> consumer, Executor executor);
```

## 6. 评审记录

| 评审人 | 时间 | 结论 | 备注 |
|--------|------|------|------|
| User | 2026-04-14 | 需修改 | 问题1：OpenAI流式聚合应优先使用SDK自带的ChatCompletionAccumulator |
| User | 2026-04-14 | 需修改 | 问题2：异步执行时，Executor应由调用方传入 |
| User | 2026-04-14 | 通过 | 方案通过，可以实施代码变更 |
| [待填写] | [待填写] | [通过/需修改] | |
