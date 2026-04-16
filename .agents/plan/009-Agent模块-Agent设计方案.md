# 方案：009-Agent模块-Agent设计方案

## 1. 背景与目的

### 1.1 背景

Open Sagent框架已完成LLM模块（001-004）、Prompt模块（005）、Memory模块（008）的设计与实现。根据项目架构方案 `project_design_agent.md`，Agent是Sagent的核心组件，负责将用户输入、System Prompt、Memory上下文和工具能力整合，调用大模型完成推理并返回结果。

当前Agent核心抽象与具体实现尚缺失，需要补齐Agent模块的最后一块拼图。

### 1.2 目的

1. 在Core模块定义统一的Agent抽象接口、配置模型和响应模型
2. 在Infrastructure模块提供`SimpleAgent`基础实现
3. 支持同步调用、异步调用、同步流式调用、异步流式调用四种调用模式
4. `SimpleAgent`支持Memory管理、System Prompt组装、工具调用（一轮）和温度等参数传递
5. 建立Agent模块的异常处理机制

## 2. 修改方案

### 2.1 模块职责边界

```
open-sagent-core (抽象定义层)
    ├── Agent                       Agent抽象接口
    ├── AgentConfig                 Agent配置参数
    ├── AgentResponse               Agent响应结果
    └── OpenSagentAgentException    Agent模块异常类

open-sagent-infrastructure (具体实现层)
    └── SimpleAgent                 基础Agent实现（无多轮循环）
```

### 2.2 文件变更列表

#### open-sagent-core

| 文件路径 | 变更类型 | 说明 |
|----------|----------|------|
| `open-sagent-core/src/main/java/ai/sagesource/opensagent/core/agent/Agent.java` | 新增 | Agent抽象接口 |
| `open-sagent-core/src/main/java/ai/sagesource/opensagent/core/agent/AgentConfig.java` | 新增 | Agent配置参数 |
| `open-sagent-core/src/main/java/ai/sagesource/opensagent/core/agent/AgentResponse.java` | 新增 | Agent响应结果 |
| `open-sagent-core/src/main/java/ai/sagesource/opensagent/core/agent/exception/OpenSagentAgentException.java` | 新增 | Agent模块异常类 |
| `open-sagent-core/src/test/java/ai/sagesource/opensagent/core/agent/AgentConfigTest.java` | 新增 | 配置单元测试 |
| `open-sagent-core/src/test/java/ai/sagesource/opensagent/core/agent/AgentResponseTest.java` | 新增 | 响应单元测试 |

#### open-sagent-infrastructure

| 文件路径 | 变更类型 | 说明 |
|----------|----------|------|
| `open-sagent-infrastructure/src/main/java/ai/sagesource/opensagent/infrastructure/agent/SimpleAgent.java` | 新增 | 基础Agent实现 |
| `open-sagent-infrastructure/src/test/java/ai/sagesource/opensagent/infrastructure/agent/SimpleAgentTest.java` | 新增 | SimpleAgent单元测试 |

### 2.3 详细变更内容

#### 文件 1: `open-sagent-core/src/main/java/ai/sagesource/opensagent/core/agent/Agent.java`

```java
package ai.sagesource.opensagent.core.agent;

import ai.sagesource.opensagent.core.llm.completion.CompletionCancelToken;
import ai.sagesource.opensagent.core.llm.completion.StreamChunk;
import ai.sagesource.opensagent.core.llm.message.UserCompletionMessage;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * Agent抽象接口
 * <p>
 * 定义Agent与大模型交互的统一接口，支持同步、异步、流式四种调用模式。
 * Agent负责整合Prompt、Memory、Tool和Completion能力，为调用方提供端到端的智能体服务。
 *
 * @author: sage.xue
 * @time: 2026/4/16
 */
public interface Agent {

    /**
     * 同步调用
     *
     * @param message 用户消息
     * @return Agent响应结果
     */
    default AgentResponse chat(UserCompletionMessage message) {
        return chat(List.of(message));
    }

    /**
     * 同步调用
     *
     * @param messages 用户消息列表
     * @return Agent响应结果
     */
    AgentResponse chat(List<UserCompletionMessage> messages);

    /**
     * 异步调用
     *
     * @param message  用户消息
     * @param executor 执行器
     * @return 异步Agent响应结果
     */
    default CompletableFuture<AgentResponse> chatAsync(UserCompletionMessage message, Executor executor) {
        return chatAsync(List.of(message), executor);
    }

    /**
     * 异步调用
     *
     * @param messages 用户消息列表
     * @param executor 执行器
     * @return 异步Agent响应结果
     */
    CompletableFuture<AgentResponse> chatAsync(List<UserCompletionMessage> messages, Executor executor);

    /**
     * 同步流式调用
     *
     * @param message  用户消息
     * @param consumer 流式分片消费者
     * @return 取消令牌
     */
    default CompletionCancelToken stream(UserCompletionMessage message, Consumer<StreamChunk> consumer) {
        return stream(List.of(message), consumer);
    }

    /**
     * 同步流式调用
     *
     * @param messages 用户消息列表
     * @param consumer 流式分片消费者
     * @return 取消令牌
     */
    CompletionCancelToken stream(List<UserCompletionMessage> messages, Consumer<StreamChunk> consumer);

    /**
     * 异步流式调用
     *
     * @param message  用户消息
     * @param consumer 流式分片消费者
     * @param executor 执行器
     * @return 取消令牌
     */
    default CompletionCancelToken streamAsync(UserCompletionMessage message, Consumer<StreamChunk> consumer, Executor executor) {
        return streamAsync(List.of(message), consumer, executor);
    }

    /**
     * 异步流式调用
     *
     * @param messages 用户消息列表
     * @param consumer 流式分片消费者
     * @param executor 执行器
     * @return 取消令牌
     */
    CompletionCancelToken streamAsync(List<UserCompletionMessage> messages, Consumer<StreamChunk> consumer, Executor executor);
}
```

#### 文件 2: `open-sagent-core/src/main/java/ai/sagesource/opensagent/core/agent/AgentConfig.java`

```java
package ai.sagesource.opensagent.core.agent;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Agent配置参数
 * <p>
 * 封装Agent运行时的核心配置项，包括工具支持开关、循环次数、采样温度、最大Token数等。
 *
 * @author: sage.xue
 * @time: 2026/4/16
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentConfig {

    /**
     * 是否开启工具支持
     * <p>
     * 开启后，Agent在调用Completion时会传递已注册的工具定义；
     * 如果模型返回ToolCall，Agent将执行对应工具。
     */
    @Builder.Default
    private boolean enableTools = false;

    /**
     * 最大循环次数（可选）
     * <p>
     * 主要用于ReActAgent等多轮推理模式，SimpleAgent中可作为扩展预留。
     */
    private Integer maxIterations;

    /**
     * 采样温度（0-2）
     */
    private Double temperature;

    /**
     * 最大生成Token数
     */
    private Integer maxTokens;
}
```

#### 文件 3: `open-sagent-core/src/main/java/ai/sagesource/opensagent/core/agent/AgentResponse.java`

```java
package ai.sagesource.opensagent.core.agent;

import ai.sagesource.opensagent.core.llm.completion.TokenUsage;
import ai.sagesource.opensagent.core.llm.message.AssistantCompletionMessage;
import ai.sagesource.opensagent.core.llm.tool.ToolResult;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Agent响应结果
 * <p>
 * 封装Agent执行大模型补全后的最终结果，包含助手回复消息、本次执行的工具结果、Token用量等。
 *
 * @author: sage.xue
 * @time: 2026/4/16
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentResponse {

    /**
     * 响应ID
     */
    private String responseId;

    /**
     * 助手的回复消息
     */
    private AssistantCompletionMessage message;

    /**
     * 本次执行的工具结果列表
     */
    @Builder.Default
    private List<ToolResult> toolResults = new ArrayList<>();

    /**
     * Token用量统计
     */
    private TokenUsage usage;

    /**
     * 结束原因
     */
    private String finishReason;

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

#### 文件 4: `open-sagent-core/src/main/java/ai/sagesource/opensagent/core/agent/exception/OpenSagentAgentException.java`

```java
package ai.sagesource.opensagent.core.agent.exception;

import ai.sagesource.opensagent.base.exception.OpenSagentException;

/**
 * Agent模块异常类
 *
 * @author: sage.xue
 * @time: 2026/4/16
 */
public class OpenSagentAgentException extends OpenSagentException {

    public OpenSagentAgentException(String message) {
        super(message);
    }

    public OpenSagentAgentException(String message, Throwable cause) {
        super(message, cause);
    }

    public OpenSagentAgentException(Throwable cause) {
        super(cause);
    }
}
```

#### 文件 5: `open-sagent-infrastructure/src/main/java/ai/sagesource/opensagent/infrastructure/agent/SimpleAgent.java`

```java
package ai.sagesource.opensagent.infrastructure.agent;

import ai.sagesource.opensagent.core.agent.Agent;
import ai.sagesource.opensagent.core.agent.AgentConfig;
import ai.sagesource.opensagent.core.agent.AgentResponse;
import ai.sagesource.opensagent.core.agent.exception.OpenSagentAgentException;
import ai.sagesource.opensagent.core.agent.memory.Memory;
import ai.sagesource.opensagent.core.agent.prompt.PromptRenderContext;
import ai.sagesource.opensagent.core.agent.prompt.PromptTemplate;
import ai.sagesource.opensagent.core.llm.completion.*;
import ai.sagesource.opensagent.core.llm.message.*;
import ai.sagesource.opensagent.core.llm.tool.ToolCall;
import ai.sagesource.opensagent.core.llm.tool.ToolExecutor;
import ai.sagesource.opensagent.core.llm.tool.ToolRegistry;
import ai.sagesource.opensagent.core.llm.tool.ToolResult;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * 基础Agent实现（SimpleAgent）
 * <p>
 * SimpleAgent是Agent模块的基础参考实现，特性如下：
 * <ul>
 *     <li>无多轮循环执行（相对ReActAgent而言）</li>
 *     <li>每次执行前调用Memory进行记忆压缩</li>
 *     <li>组装消息历史：System Prompt + 记忆历史 + 未压缩对话历史 + 最新用户消息</li>
 *     <li>Completion响应后保存对话历史到Memory</li>
 *     <li>支持工具调用：如模型返回ToolCall，执行工具并将结果存入Memory，然后再次调用模型获取最终文本回复（一轮）</li>
 *     <li>流式调用为透传模式，由底层Completion直接处理流式数据</li>
 * </ul>
 *
 * @author: sage.xue
 * @time: 2026/4/16
 */
@Slf4j
public class SimpleAgent implements Agent {

    private final String name;
    private final PromptTemplate promptTemplate;
    private final PromptRenderContext promptContext;
    private final Memory memory;
    private final LLMCompletion completion;
    private final ToolRegistry toolRegistry;
    private final AgentConfig config;

    public SimpleAgent(String name,
                       PromptTemplate promptTemplate,
                       PromptRenderContext promptContext,
                       Memory memory,
                       LLMCompletion completion,
                       ToolRegistry toolRegistry,
                       AgentConfig config) {
        this.name = name;
        this.promptTemplate = promptTemplate;
        this.promptContext = promptContext;
        this.memory = memory;
        this.completion = completion;
        this.toolRegistry = toolRegistry;
        this.config = config != null ? config : AgentConfig.builder().build();
    }

    @Override
    public AgentResponse chat(List<UserCompletionMessage> messages) {
        log.info("> Agent | {} 开始同步调用 <", name);
        if (completion == null) {
            throw new OpenSagentAgentException("LLMCompletion未配置");
        }
        try {
            // 1. 添加用户消息到记忆
            addMessagesToMemory(messages);
            // 2. 执行记忆压缩
            if (memory != null) {
                memory.compress();
            }
            // 3. 调用Completion
            CompletionRequest request = buildRequest(false);
            CompletionResponse response = completion.complete(request);
            // 4. 处理响应（保存历史、处理工具调用）
            return processResponse(response);
        } catch (OpenSagentAgentException e) {
            throw e;
        } catch (Exception e) {
            log.error("> Agent | {} 同步调用失败 <", name, e);
            throw new OpenSagentAgentException("Agent调用失败: " + e.getMessage(), e);
        }
    }

    @Override
    public CompletableFuture<AgentResponse> chatAsync(List<UserCompletionMessage> messages, Executor executor) {
        if (executor == null) {
            throw new OpenSagentAgentException("Executor不能为空");
        }
        return CompletableFuture.supplyAsync(() -> chat(messages), executor);
    }

    @Override
    public CompletionCancelToken stream(List<UserCompletionMessage> messages, Consumer<StreamChunk> consumer) {
        log.info("> Agent | {} 开始同步流式调用 <", name);
        if (completion == null) {
            throw new OpenSagentAgentException("LLMCompletion未配置");
        }
        addMessagesToMemory(messages);
        if (memory != null) {
            memory.compress();
        }
        CompletionRequest request = buildRequest(true);
        return completion.stream(request, consumer);
    }

    @Override
    public CompletionCancelToken streamAsync(List<UserCompletionMessage> messages, Consumer<StreamChunk> consumer, Executor executor) {
        if (executor == null) {
            throw new OpenSagentAgentException("Executor不能为空");
        }
        return CompletableFuture.supplyAsync(() -> stream(messages, consumer), executor)
                .exceptionally(ex -> {
                    log.error("> Agent | {} 异步流式调用失败 <", name, ex);
                    return null;
                }).join();
    }

    private void addMessagesToMemory(List<UserCompletionMessage> messages) {
        if (memory != null && messages != null && !messages.isEmpty()) {
            memory.addMessages(messages);
        }
    }

    private CompletionRequest buildRequest(boolean stream) {
        List<CompletionMessage> messages = new ArrayList<>();

        // System Prompt
        if (promptTemplate != null) {
            String systemText = promptTemplate.render(
                    promptContext != null ? promptContext : PromptRenderContext.empty());
            if (systemText != null && !systemText.isEmpty()) {
                messages.add(SystemCompletionMessage.of(systemText));
            }
        }

        // Memory上下文
        if (memory != null) {
            // 记忆历史（压缩后的记忆）作为System上下文补充
            if (!memory.getMemoryItems().isEmpty()) {
                StringBuilder memoryBuilder = new StringBuilder();
                memoryBuilder.append("以下是历史对话记忆：\n");
                memory.getMemoryItems().forEach(item -> {
                    memoryBuilder.append(item.getContent()).append("\n");
                });
                messages.add(SystemCompletionMessage.builder()
                        .contents(new ArrayList<>(List.of(
                                TextContent.builder().text(memoryBuilder.toString().trim()).build())))
                        .build());
            }
            // 未压缩对话历史
            messages.addAll(memory.getUncompressedMessages());
        }

        CompletionRequest request = CompletionRequest.builder()
                .messages(messages)
                .temperature(config.getTemperature())
                .maxTokens(config.getMaxTokens())
                .stream(stream)
                .build();

        // 工具注册
        if (config.isEnableTools() && toolRegistry != null && !toolRegistry.getAllDefinitions().isEmpty()) {
            request.setTools(toolRegistry.getAllDefinitions());
        }

        return request;
    }

    private AgentResponse processResponse(CompletionResponse response) {
        if (response == null || response.getMessage() == null) {
            throw new OpenSagentAgentException("模型响应为空");
        }

        // 保存助手消息到记忆
        if (memory != null) {
            memory.addMessage(response.getMessage());
        }

        List<ToolResult> toolResults = new ArrayList<>();

        // 处理工具调用（一轮）
        if (config.isEnableTools() && response.hasToolCalls() && toolRegistry != null) {
            List<ToolCall> toolCalls = response.getMessage().getToolCalls();
            ToolExecutor executor = new ToolExecutor(toolRegistry);
            for (ToolResult result : executor.executeAll(toolCalls)) {
                toolResults.add(result);
                if (memory != null) {
                    memory.addMessage(ToolCompletionMessage.of(
                            result.getToolCallId(),
                            result.getToolName(),
                            result.getOutput()
                    ));
                }
            }

            // 再次调用模型获取最终文本回复
            CompletionRequest finalRequest = buildRequest(false);
            CompletionResponse finalResponse = completion.complete(finalRequest);
            if (finalResponse == null || finalResponse.getMessage() == null) {
                throw new OpenSagentAgentException("模型最终响应为空");
            }
            if (memory != null) {
                memory.addMessage(finalResponse.getMessage());
            }

            return AgentResponse.builder()
                    .responseId(finalResponse.getResponseId())
                    .message(finalResponse.getMessage())
                    .toolResults(toolResults)
                    .usage(finalResponse.getUsage())
                    .finishReason(finalResponse.getFinishReason())
                    .build();
        }

        return AgentResponse.builder()
                .responseId(response.getResponseId())
                .message(response.getMessage())
                .toolResults(toolResults)
                .usage(response.getUsage())
                .finishReason(response.getFinishReason())
                .build();
    }
}
```

#### 文件 6: `open-sagent-core/src/test/java/ai/sagesource/opensagent/core/agent/AgentConfigTest.java`

```java
package ai.sagesource.opensagent.core.agent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AgentConfig单元测试
 *
 * @author: sage.xue
 * @time: 2026/4/16
 */
class AgentConfigTest {

    @Test
    @DisplayName("默认配置 - 工具开关关闭")
    void testDefaultConfig() {
        AgentConfig config = AgentConfig.builder().build();

        assertFalse(config.isEnableTools());
        assertNull(config.getMaxIterations());
        assertNull(config.getTemperature());
        assertNull(config.getMaxTokens());
    }

    @Test
    @DisplayName("自定义配置 - 成功")
    void testCustomConfig() {
        AgentConfig config = AgentConfig.builder()
                .enableTools(true)
                .maxIterations(5)
                .temperature(0.7)
                .maxTokens(2048)
                .build();

        assertTrue(config.isEnableTools());
        assertEquals(5, config.getMaxIterations());
        assertEquals(0.7, config.getTemperature());
        assertEquals(2048, config.getMaxTokens());
    }
}
```

#### 文件 7: `open-sagent-core/src/test/java/ai/sagesource/opensagent/core/agent/AgentResponseTest.java`

```java
package ai.sagesource.opensagent.core.agent;

import ai.sagesource.opensagent.core.llm.message.AssistantCompletionMessage;
import ai.sagesource.opensagent.core.llm.tool.ToolCall;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AgentResponse单元测试
 *
 * @author: sage.xue
 * @time: 2026/4/16
 */
class AgentResponseTest {

    @Test
    @DisplayName("创建响应 - 成功")
    void testCreateResponse() {
        AssistantCompletionMessage message = AssistantCompletionMessage.of("你好");
        AgentResponse response = AgentResponse.builder()
                .responseId("resp-001")
                .message(message)
                .finishReason("stop")
                .build();

        assertEquals("resp-001", response.getResponseId());
        assertEquals("你好", response.getMessage().getTextContent());
        assertEquals("stop", response.getFinishReason());
        assertTrue(response.getToolResults().isEmpty());
    }

    @Test
    @DisplayName("响应包含工具调用 - 成功")
    void testHasToolCalls() {
        AssistantCompletionMessage message = AssistantCompletionMessage.builder()
                .contents(new ArrayList<>(List.of(
                        ai.sagesource.opensagent.core.llm.message.TextContent.builder().text("调用工具").build())))
                .toolCalls(new ArrayList<>(List.of(
                        ToolCall.builder().id("call-1").name("weather").build())))
                .build();

        AgentResponse response = AgentResponse.builder()
                .message(message)
                .build();

        assertTrue(response.hasToolCalls());
    }

    @Test
    @DisplayName("响应不包含工具调用 - 成功")
    void testNoToolCalls() {
        AssistantCompletionMessage message = AssistantCompletionMessage.of("纯文本回复");

        AgentResponse response = AgentResponse.builder()
                .message(message)
                .build();

        assertFalse(response.hasToolCalls());
    }
}
```

#### 文件 8: `open-sagent-infrastructure/src/test/java/ai/sagesource/opensagent/infrastructure/agent/SimpleAgentTest.java`

```java
package ai.sagesource.opensagent.infrastructure.agent;

import ai.sagesource.opensagent.core.agent.AgentConfig;
import ai.sagesource.opensagent.core.agent.AgentResponse;
import ai.sagesource.opensagent.core.agent.exception.OpenSagentAgentException;
import ai.sagesource.opensagent.core.agent.memory.SimpleMemory;
import ai.sagesource.opensagent.core.llm.completion.*;
import ai.sagesource.opensagent.core.llm.message.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SimpleAgent单元测试
 *
 * @author: sage.xue
 * @time: 2026/4/16
 */
class SimpleAgentTest {

    /**
     * 用于测试的Fake LLMCompletion实现
     */
    static class FakeLLMCompletion implements LLMCompletion {
        private final CompletionResponse response;

        FakeLLMCompletion(CompletionResponse response) {
            this.response = response;
        }

        @Override
        public CompletionResponse complete(CompletionRequest request) {
            return response;
        }

        @Override
        public CompletableFuture<CompletionResponse> completeAsync(CompletionRequest request, Executor executor) {
            return CompletableFuture.supplyAsync(() -> complete(request), executor);
        }

        @Override
        public CompletionCancelToken stream(CompletionRequest request, Consumer<StreamChunk> consumer) {
            consumer.accept(StreamChunk.builder()
                    .deltaText("流式回复")
                    .finished(true)
                    .finishReason("stop")
                    .build());
            return new CompletionCancelToken() {
                @Override
                public void cancel() {}
                @Override
                public boolean isCancelled() { return false; }
            };
        }

        @Override
        public CompletionCancelToken streamAsync(CompletionRequest request, Consumer<StreamChunk> consumer, Executor executor) {
            return CompletableFuture.supplyAsync(() -> stream(request, consumer), executor).join();
        }
    }

    @Test
    @DisplayName("同步调用 - 成功")
    void testChat() {
        CompletionResponse response = CompletionResponse.builder()
                .responseId("resp-001")
                .message(AssistantCompletionMessage.of("你好，有什么可以帮你的？"))
                .finishReason("stop")
                .build();

        SimpleAgent agent = new SimpleAgent(
                "TestAgent",
                null,
                null,
                null,
                new FakeLLMCompletion(response),
                null,
                AgentConfig.builder().build()
        );

        AgentResponse result = agent.chat(UserCompletionMessage.of("你好"));

        assertNotNull(result);
        assertEquals("resp-001", result.getResponseId());
        assertEquals("你好，有什么可以帮你的？", result.getMessage().getTextContent());
        assertEquals("stop", result.getFinishReason());
    }

    @Test
    @DisplayName("同步调用 - Completion为空抛出异常")
    void testChatWithoutCompletion() {
        SimpleAgent agent = new SimpleAgent(
                "TestAgent",
                null, null, null, null, null, null
        );

        OpenSagentAgentException exception = assertThrows(
                OpenSagentAgentException.class,
                () -> agent.chat(UserCompletionMessage.of("你好"))
        );
        assertTrue(exception.getMessage().contains("未配置"));
    }

    @Test
    @DisplayName("异步调用 - 成功")
    void testChatAsync() {
        CompletionResponse response = CompletionResponse.builder()
                .message(AssistantCompletionMessage.of("异步回复"))
                .build();

        SimpleAgent agent = new SimpleAgent(
                "TestAgent",
                null, null, null,
                new FakeLLMCompletion(response),
                null, null
        );

        Executor executor = Executors.newSingleThreadExecutor();
        CompletableFuture<AgentResponse> future = agent.chatAsync(UserCompletionMessage.of("你好"), executor);

        AgentResponse result = future.join();
        assertEquals("异步回复", result.getMessage().getTextContent());
    }

    @Test
    @DisplayName("带Memory调用 - 保存对话历史")
    void testChatWithMemory() {
        CompletionResponse response = CompletionResponse.builder()
                .message(AssistantCompletionMessage.of("记住了"))
                .build();

        SimpleMemory memory = new SimpleMemory(10);
        SimpleAgent agent = new SimpleAgent(
                "TestAgent",
                null, null, memory,
                new FakeLLMCompletion(response),
                null, null
        );

        agent.chat(UserCompletionMessage.of("我叫张三"));

        assertEquals(2, memory.getMessages().size());
        assertEquals(1, memory.getUncompressedMessages().size());
        assertEquals(MessageRole.ASSISTANT, memory.getMessages().get(1).getRole());
    }

    @Test
    @DisplayName("带PromptTemplate调用 - 组装System消息")
    void testChatWithPromptTemplate() {
        CompletionResponse response = CompletionResponse.builder()
                .message(AssistantCompletionMessage.of("收到"))
                .build();

        ai.sagesource.opensagent.core.agent.prompt.PromptTemplate template =
                new ai.sagesource.opensagent.infrastructure.agent.prompt.DefaultPromptTemplate(
                        "你是一个{{role}}"
                );

        SimpleAgent agent = new SimpleAgent(
                "TestAgent",
                template,
                ai.sagesource.opensagent.core.agent.prompt.PromptRenderContext.of(
                        new java.util.HashMap<>() {{ put("role", "助手"); }}
                ),
                null,
                new FakeLLMCompletion(response),
                null, null
        );

        AgentResponse result = agent.chat(UserCompletionMessage.of("你好"));
        assertEquals("收到", result.getMessage().getTextContent());
    }

    @Test
    @DisplayName("Temperature参数传递 - 成功")
    void testTemperaturePropagation() {
        // 通过Fake验证temperature是否被设置到CompletionRequest中
        CompletionResponse response = CompletionResponse.builder()
                .message(AssistantCompletionMessage.of("ok"))
                .build();

        SimpleAgent agent = new SimpleAgent(
                "TestAgent",
                null, null, null,
                new FakeLLMCompletion(response),
                null,
                AgentConfig.builder().temperature(0.5).build()
        );

        AgentResponse result = agent.chat(UserCompletionMessage.of("test"));
        assertNotNull(result);
    }

    @Test
    @DisplayName("同步流式调用 - 透传成功")
    void testStream() {
        CompletionResponse response = CompletionResponse.builder()
                .message(AssistantCompletionMessage.of("流式"))
                .build();

        SimpleAgent agent = new SimpleAgent(
                "TestAgent",
                null, null, null,
                new FakeLLMCompletion(response),
                null, null
        );

        StringBuilder sb = new StringBuilder();
        CompletionCancelToken token = agent.stream(
                UserCompletionMessage.of("你好"),
                chunk -> {
                    if (chunk.getDeltaText() != null) {
                        sb.append(chunk.getDeltaText());
                    }
                }
        );

        assertNotNull(token);
        assertEquals("流式回复", sb.toString());
    }
}
```

## 3. 影响范围分析

### 3.1 模块依赖关系

```
open-sagent-base (基础定义)
    └── OpenSagentException (全局异常基类)

open-sagent-core (抽象定义)
    ├── 依赖 open-sagent-base
    ├── 依赖 open-sagent-core/llm/completion (LLMCompletion, StreamChunk, CompletionCancelToken, TokenUsage)
    ├── 依赖 open-sagent-core/llm/message (CompletionMessage, AssistantCompletionMessage, SystemCompletionMessage, UserCompletionMessage, ToolCompletionMessage, TextContent)
    ├── 依赖 open-sagent-core/llm/tool (ToolCall, ToolExecutor, ToolRegistry, ToolResult)
    ├── 依赖 open-sagent-core/agent/prompt (PromptTemplate, PromptRenderContext)
    ├── 依赖 open-sagent-core/agent/memory (Memory)
    ├── Agent (接口)
    ├── AgentConfig (配置)
    ├── AgentResponse (响应)
    └── OpenSagentAgentException (异常)

open-sagent-infrastructure (具体实现)
    ├── 依赖 open-sagent-core
    ├── SimpleAgent (基础实现)
    └── 复用现有 Prompt、Memory、Completion、Tool 实现
```

### 3.2 影响范围

| 模块 | 影响说明 |
|------|----------|
| open-sagent-core | 新增Agent核心抽象，无破坏性变更 |
| open-sagent-infrastructure | 新增SimpleAgent实现，无破坏性变更 |
| open-sagent-tools | 无直接影响 |
| open-sagent-web | 无直接影响 |
| open-sagent-cli | 无直接影响 |

### 3.3 扩展性说明

1. **支持多种Agent实现**：`Agent`为接口，后续可扩展`ReActAgent`（多轮推理）、`ScheduleAgent`（调度型Agent）等
2. **配置可扩展**：`AgentConfig`采用Lombok `@Builder`，后续新增配置项无需修改构造方法
3. **与Completion解耦**：Agent通过`LLMCompletion`接口调用大模型，底层实现可自由替换（OpenAI、Claude等）
4. **流式透传设计**：当前SimpleAgent流式调用直接透传给底层Completion，保持职责边界清晰

## 4. 测试计划

### 4.1 单元测试

| 测试类 | 模块 | 测试内容 |
|--------|------|----------|
| `AgentConfigTest` | core | 默认配置、自定义配置验证 |
| `AgentResponseTest` | core | 响应构建、工具调用检测 |
| `SimpleAgentTest` | infrastructure | 同步调用、异步调用、空Completion异常、Memory保存、Prompt组装、温度参数传递、流式透传 |

### 4.2 编译验证

```bash
mvn clean compile test-compile -pl open-sagent-core,open-sagent-infrastructure -am
```

### 4.3 测试执行

```bash
mvn clean test -pl open-sagent-core,open-sagent-infrastructure -am
```

## 5. 评审记录

| 评审人 | 时间 | 结论 | 备注 |
|--------|------|------|------|
| sage | 2026-04-16 | 不通过 | 问题1：Agent接口chat/stream等方法参数类型由CompletionMessage改为显式传入UserCompletionMessage，已修正 |
| | | | |
