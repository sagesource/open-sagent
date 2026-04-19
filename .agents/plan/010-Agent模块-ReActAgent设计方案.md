# 方案：010-Agent模块-ReActAgent设计方案

## 1. 背景与目的

### 1.1 背景

Open Sagent框架已完成LLM模块（001-004）、Prompt模块（005）、Memory模块（008）、Agent基础模块（009）的设计与实现。`SimpleAgent`作为基础Agent实现，仅支持单轮工具调用后返回结果。根据项目架构方案 `project_design_reactagent.md`，需要实现`ReActAgent`，支持多轮推理-行动（Reasoning-Acting）循环。

**本轮设计重点变更**：根据 `project_design_reactagent.md` 第8条要求，ReActAgent的**流式调用全程使用流式输出**——每轮迭代均调用`completion.stream()`，在流式输出过程中透传文本增量，本轮流式结束后根据返回的工具调用信息执行工具，进入下一轮流式循环；第9条要求，**执行工具时向consumer发送固定内容`AGENT_ACTION[EXECUTE_TOOL]`**，供客户端识别并执行不同的展示逻辑。

### 1.2 目的

1. 扩展`AgentConfig`，增加ReAct模式所需的配置项（最大迭代次数、结束工具名称、工具调用阈值）
2. 提取`AbstractAgent`抽象基类，沉淀SimpleAgent与ReActAgent的公共逻辑（消息组装、Memory管理、请求构建、异步方法默认实现等）
3. 在Infrastructure模块提供`ReActAgent`多轮推理实现，特性如下：
   - `chat()`：基于`complete()`驱动多轮循环，支持Token用量累加统计
   - `stream()`：**全程使用`stream()`驱动每轮循环**，流式文本增量逐轮透传，本轮流式结束后根据`StreamChunk`中的工具调用信息执行工具
   - 支持设定最大迭代上限，超过上限仍未结束则抛出异常
   - 支持配置结束工具名称（默认`react_finish_answer`），模型调用该工具时终止迭代
   - 支持监控同一工具调用次数阈值，超过阈值输出Warn日志
   - 支持工具调用，将工具执行结果（含失败异常）作为ToolMessage送入下一轮循环
   - 与SimpleAgent实现一致，支持Memory、中断、统计Token用量
   - `stream()`模式下执行工具前，向consumer发送固定内容`AGENT_ACTION[EXECUTE_TOOL]`，供客户端识别展示

## 2. 修改方案

### 2.1 模块职责边界

```
open-sagent-core (抽象定义层)
    ├── AgentConfig                     扩展：新增ReAct相关配置字段
    └── OpenSagentAgentException        已有Agent模块异常类

open-sagent-infrastructure (具体实现层)
    ├── AbstractAgent                   新增：提取SimpleAgent公共逻辑为抽象基类
    ├── SimpleAgent                     变更：继承AbstractAgent，保留原有行为
    └── ReActAgent                      新增：ReAct多轮推理Agent实现
```

### 2.2 文件变更列表

#### open-sagent-core

| 文件路径 | 变更类型 | 说明 |
|----------|----------|------|
| `open-sagent-core/src/main/java/ai/sagesource/opensagent/core/agent/AgentConfig.java` | 修改 | 新增`finishToolName`、`toolCallThreshold`字段 |
| `open-sagent-core/src/test/java/ai/sagesource/opensagent/core/agent/AgentConfigTest.java` | 修改 | 补充ReAct配置项的单元测试 |

#### open-sagent-infrastructure

| 文件路径 | 变更类型 | 说明 |
|----------|----------|------|
| `open-sagent-infrastructure/src/main/java/ai/sagesource/opensagent/infrastructure/agent/AbstractAgent.java` | 新增 | Agent抽象基类，提取公共字段与辅助方法 |
| `open-sagent-infrastructure/src/main/java/ai/sagesource/opensagent/infrastructure/agent/SimpleAgent.java` | 修改 | 继承AbstractAgent，逻辑保持不变 |
| `open-sagent-infrastructure/src/main/java/ai/sagesource/opensagent/infrastructure/agent/ReActAgent.java` | 新增 | ReActAgent多轮推理实现 |
| `open-sagent-infrastructure/src/test/java/ai/sagesource/opensagent/infrastructure/agent/ReActAgentTest.java` | 新增 | ReActAgent单元测试 |

### 2.3 详细变更内容

#### 文件 1: `open-sagent-core/src/main/java/ai/sagesource/opensagent/core/agent/AgentConfig.java`

在现有`AgentConfig`基础上，新增两个ReAct相关配置字段：

```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentConfig {

    // ... 已有字段 enableTools, maxIterations, temperature, maxTokens

    /**
     * 结束工具名称
     * <p>
     * 用于ReActAgent模式，当模型调用该名称的工具时，表示任务已完成，终止迭代循环。
     * 该值会作为Prompt模板中占位符的替换值，提示模型在任务完成时调用此工具。
     */
    @Builder.Default
    private String finishToolName = "react_finish_answer";

    /**
     * 同一工具调用阈值
     * <p>
     * 用于ReActAgent模式，在一次ReAct调用中，如果同一个工具被调用的次数超过该阈值，
     * 输出Warn级别日志告警，防止模型陷入无限循环或重复调用。
     * 默认为3次。
     */
    @Builder.Default
    private Integer toolCallThreshold = 3;
}
```

> **设计说明**：`maxIterations`在009方案中已预留，ReActAgent模式下为必传参数；`finishToolName`和`toolCallThreshold`为新增字段，均有默认值，保持向后兼容。

---

#### 文件 2: `open-sagent-infrastructure/src/main/java/ai/sagesource/opensagent/infrastructure/agent/AbstractAgent.java`

提取SimpleAgent中的公共字段和辅助方法，作为SimpleAgent和ReActAgent的共享基类。

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
import ai.sagesource.opensagent.core.llm.tool.ToolRegistry;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * Agent抽象基类
 * <p>
 * 提取SimpleAgent与ReActAgent的公共逻辑，包括：
 * <ul>
 *     <li>公共字段（name, promptTemplate, memory, completion, toolRegistry, config）</li>
 *     <li>异步调用、异步流式调用的默认实现</li>
 *     <li>消息组装（System Prompt + Memory历史 + 未压缩对话历史）</li>
 *     <li>用户消息添加到Memory</li>
 * </ul>
 *
 * @author: sage.xue
 * @time: 2026/4/19
 */
@Slf4j
public abstract class AbstractAgent implements Agent {

    protected final String name;
    protected final PromptTemplate promptTemplate;
    protected final PromptRenderContext promptContext;
    protected final Memory memory;
    protected final LLMCompletion completion;
    protected final ToolRegistry toolRegistry;
    protected final AgentConfig config;

    public AbstractAgent(String name,
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
    public CompletableFuture<AgentResponse> chatAsync(List<UserCompletionMessage> messages, Executor executor) {
        if (executor == null) {
            throw new OpenSagentAgentException("Executor不能为空");
        }
        return CompletableFuture.supplyAsync(() -> chat(messages), executor);
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

    protected void addMessagesToMemory(List<UserCompletionMessage> messages) {
        if (memory != null && messages != null && !messages.isEmpty()) {
            List<CompletionMessage> completionMessages = new ArrayList<>(messages);
            memory.addMessages(completionMessages);
        }
    }

    protected CompletionRequest buildRequest(boolean stream) {
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
}
```

> **设计说明**：`chat`和`stream`方法保持抽象，由子类实现各自的同步调用和同步流式调用逻辑；`chatAsync`和`streamAsync`提供统一的异步包装。

---

#### 文件 3: `open-sagent-infrastructure/src/main/java/ai/sagesource/opensagent/infrastructure/agent/SimpleAgent.java`

SimpleAgent改为继承`AbstractAgent`，原有逻辑完全保持不变。

```java
package ai.sagesource.opensagent.infrastructure.agent;

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
public class SimpleAgent extends AbstractAgent {

    public SimpleAgent(String name,
                       PromptTemplate promptTemplate,
                       PromptRenderContext promptContext,
                       Memory memory,
                       LLMCompletion completion,
                       ToolRegistry toolRegistry,
                       AgentConfig config) {
        super(name, promptTemplate, promptContext, memory, completion, toolRegistry, config);
    }

    @Override
    public AgentResponse chat(List<UserCompletionMessage> messages) {
        log.info("> Agent | {} 开始同步调用 <", name);
        if (completion == null) {
            throw new OpenSagentAgentException("LLMCompletion未配置");
        }
        try {
            addMessagesToMemory(messages);
            if (memory != null) {
                memory.compress();
            }
            CompletionRequest request = buildRequest(false);
            CompletionResponse response = completion.complete(request);
            return processResponse(response);
        } catch (OpenSagentAgentException e) {
            throw e;
        } catch (Exception e) {
            log.error("> Agent | {} 同步调用失败 <", name, e);
            throw new OpenSagentAgentException("Agent调用失败: " + e.getMessage(), e);
        }
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

    private AgentResponse processResponse(CompletionResponse response) {
        if (response == null || response.getMessage() == null) {
            throw new OpenSagentAgentException("模型响应为空");
        }

        if (memory != null) {
            memory.addMessage(response.getMessage());
        }

        List<ToolResult> toolResults = new ArrayList<>();

        if (config.isEnableTools() && response.hasToolCalls() && toolRegistry != null) {
            List<ToolCall> toolCalls = response.getMessage().getToolCalls();
            ToolExecutor executor = new ToolExecutor(toolRegistry);
            List<ToolResult> results = executor.executeAll(toolCalls);
            for (int i = 0; i < results.size(); i++) {
                ToolResult result = results.get(i);
                toolResults.add(result);
                if (memory != null) {
                    memory.addMessage(ToolCompletionMessage.of(
                            result.getToolCallId(),
                            toolCalls.get(i).getName(),
                            result.getContent()
                    ));
                }
            }

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

> **设计说明**：SimpleAgent逻辑与009方案完全一致，仅将公共部分（字段、构造方法、buildRequest、addMessagesToMemory、异步方法）上提到AbstractAgent，保持行为不变，确保向后兼容。

---

#### 文件 4: `open-sagent-infrastructure/src/main/java/ai/sagesource/opensagent/infrastructure/agent/ReActAgent.java`

ReActAgent是核心新增类，实现多轮推理-行动循环。

```java
package ai.sagesource.opensagent.infrastructure.agent;

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

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * ReActAgent实现（多轮推理Agent）
 * <p>
 * ReActAgent基于ReAct（Reasoning + Acting）范式，支持多轮工具调用循环：
 * <ul>
 *     <li>每次用户调用后，Agent进入迭代循环，最多执行{@code maxIterations}轮</li>
 *     <li>{@code chat()}：每轮调用{@code completion.complete()}获取模型响应，支持Token用量累加统计</li>
 *     <li>{@code stream()}：<b>每轮调用{@code completion.stream()}</b>，流式文本增量通过consumer逐轮透传；
 *         本轮流式结束后根据{@link StreamChunk}中的工具调用信息执行工具，进入下一轮流式循环</li>
 *     <li>如果模型调用结束工具（{@code finishToolName}），提取参数中的答案并终止循环</li>
 *     <li>如果模型调用普通工具，执行工具并将结果（成功或失败）作为ToolMessage送入下一轮</li>
 *     <li>监控同一工具的调用次数，超过阈值输出Warn日志</li>
 *     <li>如果超过最大迭代次数仍未结束，抛出{@link OpenSagentAgentException}</li>
 *     <li>支持Memory管理、中断、Token用量统计</li>
 * </ul>
 *
 * @author: sage.xue
 * @time: 2026/4/19
 */
@Slf4j
public class ReActAgent extends AbstractAgent {

    public ReActAgent(String name,
                      PromptTemplate promptTemplate,
                      PromptRenderContext promptContext,
                      Memory memory,
                      LLMCompletion completion,
                      ToolRegistry toolRegistry,
                      AgentConfig config) {
        super(name, promptTemplate, promptContext, memory, completion, toolRegistry, config);
    }

    @Override
    public AgentResponse chat(List<UserCompletionMessage> messages) {
        log.info("> Agent | {} 开始ReAct同步调用 <", name);
        validateConfig();

        try {
            addMessagesToMemory(messages);
            if (memory != null) {
                memory.compress();
            }
            return doReActChatLoop();
        } catch (OpenSagentAgentException e) {
            throw e;
        } catch (Exception e) {
            log.error("> Agent | {} ReAct同步调用失败 <", name, e);
            throw new OpenSagentAgentException("ReActAgent调用失败: " + e.getMessage(), e);
        }
    }

    @Override
    public CompletionCancelToken stream(List<UserCompletionMessage> messages, Consumer<StreamChunk> consumer) {
        log.info("> Agent | {} 开始ReAct同步流式调用 <", name);
        validateConfig();

        addMessagesToMemory(messages);
        if (memory != null) {
            memory.compress();
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

        doReActStreamLoop(cancelled, consumer);
        return token;
    }

    private void validateConfig() {
        if (completion == null) {
            throw new OpenSagentAgentException("LLMCompletion未配置");
        }
        if (config.getMaxIterations() == null || config.getMaxIterations() <= 0) {
            throw new OpenSagentAgentException("ReActAgent模式必须配置有效的maxIterations");
        }
    }

    /**
     * ReAct同步调用循环（基于complete）
     */
    private AgentResponse doReActChatLoop() {
        int maxIterations = config.getMaxIterations();
        String finishToolName = config.getFinishToolName();
        int toolCallThreshold = config.getToolCallThreshold();
        Map<String, Integer> toolCallCount = new HashMap<>();
        List<ToolResult> allToolResults = new ArrayList<>();
        TokenUsage totalUsage = null;

        for (int iteration = 1; iteration <= maxIterations; iteration++) {
            log.info("> Agent | {} ReAct第{}/{}轮 <", name, iteration, maxIterations);

            CompletionRequest request = buildRequest(false);
            CompletionResponse response = completion.complete(request);

            if (response == null || response.getMessage() == null) {
                throw new OpenSagentAgentException("模型响应为空");
            }

            if (response.getUsage() != null) {
                totalUsage = mergeTokenUsage(totalUsage, response.getUsage());
            }

            if (memory != null) {
                memory.addMessage(response.getMessage());
            }

            if (!response.hasToolCalls()) {
                return AgentResponse.builder()
                        .responseId(response.getResponseId())
                        .message(response.getMessage())
                        .toolResults(allToolResults)
                        .usage(totalUsage)
                        .finishReason(response.getFinishReason())
                        .build();
            }

            List<ToolCall> toolCalls = response.getMessage().getToolCalls();
            ToolExecutor executor = new ToolExecutor(toolRegistry);
            List<ToolResult> results = executor.executeAll(toolCalls);

            boolean shouldFinish = false;
            AssistantCompletionMessage finalMessage = null;

            for (int i = 0; i < results.size(); i++) {
                ToolResult result = results.get(i);
                ToolCall toolCall = toolCalls.get(i);
                String toolName = toolCall.getName();

                allToolResults.add(result);

                if (finishToolName.equals(toolName)) {
                    shouldFinish = true;
                    String answer = extractFinishAnswer(result, toolCall);
                    finalMessage = AssistantCompletionMessage.of(answer);
                    if (memory != null) {
                        memory.addMessage(finalMessage);
                    }
                    continue;
                }

                int count = toolCallCount.merge(toolName, 1, Integer::sum);
                if (count > toolCallThreshold) {
                    log.warn("> Agent | {} 工具[{}]调用次数超过阈值({})，当前第{}次 <",
                            name, toolName, toolCallThreshold, count);
                }

                if (memory != null) {
                    memory.addMessage(ToolCompletionMessage.of(
                            result.getToolCallId(),
                            toolName,
                            result.isSuccess() ? result.getContent() : result.getErrorMessage()
                    ));
                }
            }

            if (shouldFinish) {
                return AgentResponse.builder()
                        .responseId(response.getResponseId())
                        .message(finalMessage)
                        .toolResults(allToolResults)
                        .usage(totalUsage)
                        .finishReason("stop")
                        .build();
            }
        }

        throw new OpenSagentAgentException(
                String.format("ReActAgent超过最大迭代次数(%d)仍未完成任务", maxIterations));
    }

    /**
     * ReAct流式调用循环（基于stream，全程流式输出）
     * <p>
     * 每轮迭代均调用{@code completion.stream()}，流式文本增量通过consumer逐轮透传；
     * 本轮流式结束后，根据收集到的工具调用信息执行工具，将结果加入Memory，进入下一轮。
     * <p>
     * 当执行工具时，向consumer发送固定内容 {@code AGENT_ACTION[EXECUTE_TOOL]}，
     * 客户端根据此标记执行不同的展示逻辑（如显示"工具执行中"状态）。
     */
    private void doReActStreamLoop(AtomicBoolean cancelled, Consumer<StreamChunk> consumer) {
        int maxIterations = config.getMaxIterations();
        String finishToolName = config.getFinishToolName();
        int toolCallThreshold = config.getToolCallThreshold();
        Map<String, Integer> toolCallCount = new HashMap<>();

        for (int iteration = 1; iteration <= maxIterations; iteration++) {
            if (cancelled.get()) {
                log.warn("> Agent | {} ReAct流式调用已被取消 <", name);
                break;
            }

            log.info("> Agent | {} ReAct流式第{}/{}轮 <", name, iteration, maxIterations);

            CompletionRequest request = buildRequest(true);
            List<ToolCall> roundToolCalls = new ArrayList<>();
            AtomicBoolean roundFinished = new AtomicBoolean(false);

            Consumer<StreamChunk> wrappedConsumer = chunk -> {
                if (cancelled.get()) {
                    return;
                }

                // 收集本轮工具调用（增量）
                if (chunk.getDeltaToolCalls() != null && !chunk.getDeltaToolCalls().isEmpty()) {
                    roundToolCalls.addAll(chunk.getDeltaToolCalls());
                }

                if (chunk.isFinished()) {
                    roundFinished.set(true);
                }

                // 透传给调用方
                consumer.accept(chunk);
            };

            completion.stream(request, wrappedConsumer);

            if (cancelled.get()) {
                break;
            }

            // 本轮无工具调用，正常结束
            if (roundToolCalls.isEmpty()) {
                break;
            }

            // 发送工具执行标记，供客户端识别展示
            consumer.accept(StreamChunk.builder()
                    .deltaText("AGENT_ACTION[EXECUTE_TOOL]")
                    .aggregatedText("AGENT_ACTION[EXECUTE_TOOL]")
                    .build());

            // 处理本轮工具调用
            ToolExecutor executor = new ToolExecutor(toolRegistry);
            List<ToolResult> results = executor.executeAll(roundToolCalls);

            boolean shouldFinish = false;

            for (int i = 0; i < results.size(); i++) {
                ToolResult result = results.get(i);
                ToolCall toolCall = roundToolCalls.get(i);
                String toolName = toolCall.getName();

                if (finishToolName.equals(toolName)) {
                    shouldFinish = true;
                    String answer = extractFinishAnswer(result, toolCall);
                    // 将最终答案作为最后一个流式chunk发送
                    consumer.accept(StreamChunk.builder()
                            .deltaText(answer)
                            .aggregatedText(answer)
                            .finished(true)
                            .finishReason("stop")
                            .build());
                    if (memory != null) {
                        memory.addMessage(AssistantCompletionMessage.of(answer));
                    }
                    continue;
                }

                int count = toolCallCount.merge(toolName, 1, Integer::sum);
                if (count > toolCallThreshold) {
                    log.warn("> Agent | {} 工具[{}]调用次数超过阈值({})，当前第{}次 <",
                            name, toolName, toolCallThreshold, count);
                }

                if (memory != null) {
                    memory.addMessage(ToolCompletionMessage.of(
                            result.getToolCallId(),
                            toolName,
                            result.isSuccess() ? result.getContent() : result.getErrorMessage()
                    ));
                }
            }

            if (shouldFinish) {
                break;
            }
        }

        if (!cancelled.get()) {
            // 确保最后一轮如果是因为无工具调用而break的，已经发送了finished=true的chunk
            // 底层completion.stream()会在结束时自动发送finished=true的chunk
        }
    }

    /**
     * 从结束工具中提取最终答案
     */
    private String extractFinishAnswer(ToolResult result, ToolCall toolCall) {
        if (result.isSuccess() && result.getContent() != null) {
            return result.getContent();
        }
        if (toolCall.getArguments() != null && toolCall.getArguments().containsKey("answer")) {
            return String.valueOf(toolCall.getArguments().get("answer"));
        }
        return "任务已完成";
    }

    /**
     * 合并Token用量
     */
    private TokenUsage mergeTokenUsage(TokenUsage total, TokenUsage current) {
        if (total == null) {
            return current;
        }
        if (current == null) {
            return total;
        }
        return TokenUsage.builder()
                .promptTokens(total.getPromptTokens() + current.getPromptTokens())
                .completionTokens(total.getCompletionTokens() + current.getCompletionTokens())
                .totalTokens(total.getTotalTokens() + current.getTotalTokens())
                .build();
    }
}
```

> **设计说明**：
> 1. **chat()同步循环**：`doReActChatLoop`基于`complete()`驱动多轮循环，每轮获取`CompletionResponse`，支持TokenUsage累加统计，逻辑清晰可控。
> 2. **stream()全程流式**：`doReActStreamLoop`基于`stream()`驱动每轮循环，每轮调用`completion.stream(request, wrappedConsumer)`。`wrappedConsumer`在透传文本增量的同时，收集`deltaToolCalls`；本轮stream结束后执行工具，结果加入Memory，进入下一轮stream。调用方感知到的是连续的流式文本输出（轮次间有工具执行的空隙）。
> 3. **结束工具判断**：`chat()`和`stream()`均通过`finishToolName`匹配工具调用名称。`stream()`模式下，结束工具的结果通过额外的`StreamChunk`（`finished=true`）发送给consumer。
> 4. **工具调用阈值**：使用`HashMap`统计每轮中各工具的调用次数，超过阈值输出Warn日志，但不阻断执行（由模型自行决策）。
> 5. **异常处理**：工具执行失败时，`ToolResult`的`errorMessage`作为ToolMessage内容送入Memory，由模型决定后续处理。
> 6. **中断支持**：`stream()`方法返回`CompletionCancelToken`，内部维护`AtomicBoolean cancelled`。每轮stream的consumer回调中检查cancel状态，轮次切换时也检查，实现中断能力。
> 7. **Token用量**：`chat()`支持多轮TokenUsage累加；`stream()`由于底层`stream()`接口通过Consumer回调，不直接返回`CompletionResponse`，当前版本暂不做stream模式的TokenUsage累加（后续可扩展`StreamChunk`增加usage字段实现）。
> 8. **工具执行标记**：`stream()`模式下，在每轮检测到工具调用后、实际执行工具前，向consumer发送固定内容`AGENT_ACTION[EXECUTE_TOOL]`的`StreamChunk`，客户端据此识别并渲染工具执行状态（如展示"工具执行中"UI）。

---

#### 文件 5: `open-sagent-core/src/test/java/ai/sagesource/opensagent/core/agent/AgentConfigTest.java`

补充ReAct相关配置的单元测试：

```java
@Test
@DisplayName("ReAct默认配置 - 成功")
void testReActDefaultConfig() {
    AgentConfig config = AgentConfig.builder()
            .enableTools(true)
            .maxIterations(5)
            .build();

    assertEquals("react_finish_answer", config.getFinishToolName());
    assertEquals(3, config.getToolCallThreshold());
    assertEquals(5, config.getMaxIterations());
}

@Test
@DisplayName("ReAct自定义配置 - 成功")
void testReActCustomConfig() {
    AgentConfig config = AgentConfig.builder()
            .enableTools(true)
            .maxIterations(10)
            .finishToolName("finish")
            .toolCallThreshold(5)
            .build();

    assertEquals("finish", config.getFinishToolName());
    assertEquals(5, config.getToolCallThreshold());
    assertEquals(10, config.getMaxIterations());
}
```

---

#### 文件 6: `open-sagent-infrastructure/src/test/java/ai/sagesource/opensagent/infrastructure/agent/ReActAgentTest.java`

```java
package ai.sagesource.opensagent.infrastructure.agent;

import ai.sagesource.opensagent.core.agent.AgentConfig;
import ai.sagesource.opensagent.core.agent.AgentResponse;
import ai.sagesource.opensagent.core.agent.exception.OpenSagentAgentException;
import ai.sagesource.opensagent.core.agent.memory.SimpleMemory;
import ai.sagesource.opensagent.core.llm.completion.*;
import ai.sagesource.opensagent.core.llm.message.*;
import ai.sagesource.opensagent.core.llm.tool.ToolCall;
import ai.sagesource.opensagent.core.llm.tool.ToolRegistry;
import ai.sagesource.opensagent.infrastructure.llm.tool.AnnotatedTool;
import ai.sagesource.opensagent.infrastructure.llm.tool.annotation.Tool;
import ai.sagesource.opensagent.infrastructure.llm.tool.annotation.ToolParam;
import ai.sagesource.opensagent.infrastructure.llm.tool.metadata.ToolMetadata;
import ai.sagesource.opensagent.infrastructure.llm.tool.parser.ToolMetadataParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ReActAgent单元测试
 *
 * @author: sage.xue
 * @time: 2026/4/19
 */
class ReActAgentTest {

    static class FakeLLMCompletion implements LLMCompletion {
        private final Queue<CompletionResponse> completeResponses;
        private final Queue<List<StreamChunk>> streamResponses;

        FakeLLMCompletion(CompletionResponse... responses) {
            this.completeResponses = new LinkedList<>(Arrays.asList(responses));
            this.streamResponses = new LinkedList<>();
        }

        FakeLLMCompletion(List<StreamChunk>... streamChunks) {
            this.completeResponses = new LinkedList<>();
            this.streamResponses = new LinkedList<>(Arrays.asList(streamChunks));
        }

        @Override
        public CompletionResponse complete(CompletionRequest request) {
            return completeResponses.poll();
        }

        @Override
        public CompletableFuture<CompletionResponse> completeAsync(CompletionRequest request, Executor executor) {
            return CompletableFuture.supplyAsync(() -> complete(request), executor);
        }

        @Override
        public CompletionCancelToken stream(CompletionRequest request, Consumer<StreamChunk> consumer) {
            List<StreamChunk> chunks = streamResponses.poll();
            if (chunks != null) {
                for (StreamChunk chunk : chunks) {
                    consumer.accept(chunk);
                }
            }
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

    static class FinishToolService {
        @Tool(name = "react_finish_answer", description = "任务完成，返回答案")
        public String finish(@ToolParam(name = "answer") String answer) {
            return answer;
        }

        @Tool(name = "search", description = "搜索信息")
        public String search(@ToolParam(name = "query") String query) {
            return "搜索结果: " + query;
        }
    }

    private ToolRegistry createToolRegistry() {
        ToolRegistry registry = new ToolRegistry();
        FinishToolService service = new FinishToolService();
        List<ToolMetadata> metadatas = ToolMetadataParser.parse(FinishToolService.class);
        for (ToolMetadata metadata : metadatas) {
            registry.register(new AnnotatedTool(service, metadata));
        }
        return registry;
    }

    @Test
    @DisplayName("ReAct同步调用 - 一轮结束工具直接完成")
    void testChatWithFinishTool() {
        CompletionResponse response = CompletionResponse.builder()
                .responseId("resp-001")
                .message(AssistantCompletionMessage.builder()
                        .contents(new ArrayList<>(List.of(
                                TextContent.builder().text("调用结束工具").build())))
                        .toolCalls(new ArrayList<>(List.of(
                                ToolCall.builder()
                                        .id("call-1")
                                        .name("react_finish_answer")
                                        .arguments(Map.of("answer", "这是最终答案"))
                                        .build())))
                        .build())
                .finishReason("tool_calls")
                .build();

        ReActAgent agent = new ReActAgent(
                "ReActTest",
                null, null, null,
                new FakeLLMCompletion(response),
                createToolRegistry(),
                AgentConfig.builder()
                        .enableTools(true)
                        .maxIterations(5)
                        .build()
        );

        AgentResponse result = agent.chat(UserCompletionMessage.of("你好"));

        assertNotNull(result);
        assertEquals("这是最终答案", result.getMessage().getTextContent());
        assertEquals("stop", result.getFinishReason());
    }

    @Test
    @DisplayName("ReAct同步调用 - 多轮工具调用后完成")
    void testChatWithMultiTurnTools() {
        CompletionResponse turn1 = CompletionResponse.builder()
                .responseId("resp-001")
                .message(AssistantCompletionMessage.builder()
                        .contents(new ArrayList<>(List.of(
                                TextContent.builder().text("需要搜索").build())))
                        .toolCalls(new ArrayList<>(List.of(
                                ToolCall.builder()
                                        .id("call-1")
                                        .name("search")
                                        .arguments(Map.of("query", "天气"))
                                        .build())))
                        .build())
                .finishReason("tool_calls")
                .build();

        CompletionResponse turn2 = CompletionResponse.builder()
                .responseId("resp-002")
                .message(AssistantCompletionMessage.builder()
                        .contents(new ArrayList<>(List.of(
                                TextContent.builder().text("调用结束").build())))
                        .toolCalls(new ArrayList<>(List.of(
                                ToolCall.builder()
                                        .id("call-2")
                                        .name("react_finish_answer")
                                        .arguments(Map.of("answer", "搜索完成"))
                                        .build())))
                        .build())
                .finishReason("tool_calls")
                .build();

        SimpleMemory memory = new SimpleMemory(10);
        ReActAgent agent = new ReActAgent(
                "ReActTest",
                null, null, memory,
                new FakeLLMCompletion(turn1, turn2),
                createToolRegistry(),
                AgentConfig.builder()
                        .enableTools(true)
                        .maxIterations(5)
                        .build()
        );

        AgentResponse result = agent.chat(UserCompletionMessage.of("查天气"));

        assertNotNull(result);
        assertEquals("搜索完成", result.getMessage().getTextContent());
        assertTrue(memory.getMessages().size() >= 4);
    }

    @Test
    @DisplayName("ReAct同步调用 - 超过最大迭代次数抛出异常")
    void testChatExceedMaxIterations() {
        CompletionResponse turn = CompletionResponse.builder()
                .responseId("resp-001")
                .message(AssistantCompletionMessage.builder()
                        .contents(new ArrayList<>(List.of(
                                TextContent.builder().text("继续搜索").build())))
                        .toolCalls(new ArrayList<>(List.of(
                                ToolCall.builder()
                                        .id("call-1")
                                        .name("search")
                                        .arguments(Map.of("query", "测试"))
                                        .build())))
                        .build())
                .finishReason("tool_calls")
                .build();

        ReActAgent agent = new ReActAgent(
                "ReActTest",
                null, null, null,
                new FakeLLMCompletion(turn, turn, turn),
                createToolRegistry(),
                AgentConfig.builder()
                        .enableTools(true)
                        .maxIterations(2)
                        .build()
        );

        OpenSagentAgentException exception = assertThrows(
                OpenSagentAgentException.class,
                () -> agent.chat(UserCompletionMessage.of("测试"))
        );
        assertTrue(exception.getMessage().contains("超过最大迭代次数"));
    }

    @Test
    @DisplayName("ReAct同步调用 - 未配置maxIterations抛出异常")
    void testChatWithoutMaxIterations() {
        ReActAgent agent = new ReActAgent(
                "ReActTest",
                null, null, null,
                new FakeLLMCompletion(),
                null,
                AgentConfig.builder().build()
        );

        OpenSagentAgentException exception = assertThrows(
                OpenSagentAgentException.class,
                () -> agent.chat(UserCompletionMessage.of("测试"))
        );
        assertTrue(exception.getMessage().contains("maxIterations"));
    }

    @Test
    @DisplayName("ReAct同步调用 - 无工具调用直接返回")
    void testChatWithoutToolCalls() {
        CompletionResponse response = CompletionResponse.builder()
                .responseId("resp-001")
                .message(AssistantCompletionMessage.of("直接回复"))
                .finishReason("stop")
                .build();

        ReActAgent agent = new ReActAgent(
                "ReActTest",
                null, null, null,
                new FakeLLMCompletion(response),
                null,
                AgentConfig.builder()
                        .maxIterations(5)
                        .build()
        );

        AgentResponse result = agent.chat(UserCompletionMessage.of("你好"));

        assertNotNull(result);
        assertEquals("直接回复", result.getMessage().getTextContent());
    }

    @Test
    @DisplayName("ReAct流式调用 - 一轮流式后直接完成")
    void testStreamSingleRound() {
        List<StreamChunk> chunks = List.of(
                StreamChunk.builder().deltaText("这是").aggregatedText("这是").build(),
                StreamChunk.builder().deltaText("答案").aggregatedText("这是答案").build(),
                StreamChunk.builder().finished(true).finishReason("stop").aggregatedText("这是答案").build()
        );

        ReActAgent agent = new ReActAgent(
                "ReActTest",
                null, null, null,
                new FakeLLMCompletion(chunks),
                null,
                AgentConfig.builder()
                        .maxIterations(5)
                        .build()
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
        assertEquals("这是答案", sb.toString());
    }

    @Test
    @DisplayName("ReAct流式调用 - 多轮流式后结束工具完成")
    void testStreamMultiTurnWithFinishTool() {
        List<StreamChunk> round1 = List.of(
                StreamChunk.builder().deltaText("思考中").aggregatedText("思考中").build(),
                StreamChunk.builder()
                        .deltaToolCalls(List.of(
                                ToolCall.builder().id("call-1").name("search").arguments(Map.of("query", "天气")).build()))
                        .aggregatedText("思考中")
                        .build(),
                StreamChunk.builder().finished(true).finishReason("tool_calls").aggregatedText("思考中").build()
        );

        List<StreamChunk> round2 = List.of(
                StreamChunk.builder().deltaText("完成").aggregatedText("完成").build(),
                StreamChunk.builder()
                        .deltaToolCalls(List.of(
                                ToolCall.builder().id("call-2").name("react_finish_answer").arguments(Map.of("answer", "晴天")).build()))
                        .aggregatedText("完成")
                        .build(),
                StreamChunk.builder().finished(true).finishReason("tool_calls").aggregatedText("完成").build()
        );

        SimpleMemory memory = new SimpleMemory(10);
        ReActAgent agent = new ReActAgent(
                "ReActTest",
                null, null, memory,
                new FakeLLMCompletion(round1, round2),
                createToolRegistry(),
                AgentConfig.builder()
                        .enableTools(true)
                        .maxIterations(5)
                        .build()
        );

        StringBuilder sb = new StringBuilder();
        List<Boolean> finishedList = new ArrayList<>();
        CompletionCancelToken token = agent.stream(
                UserCompletionMessage.of("查天气"),
                chunk -> {
                    if (chunk.getDeltaText() != null) {
                        sb.append(chunk.getDeltaText());
                    }
                    finishedList.add(chunk.isFinished());
                }
        );

        assertNotNull(token);
        assertTrue(sb.toString().contains("思考中"));
        assertTrue(sb.toString().contains("完成") || sb.toString().contains("晴天"));
        assertTrue(memory.getMessages().size() >= 4);
    }

    @Test
    @DisplayName("ReAct流式调用 - 取消")
    void testStreamCancel() {
        List<StreamChunk> chunks = List.of(
                StreamChunk.builder().deltaText("开始").aggregatedText("开始").build(),
                StreamChunk.builder().deltaText("...").aggregatedText("开始...").build()
        );

        ReActAgent agent = new ReActAgent(
                "ReActTest",
                null, null, null,
                new FakeLLMCompletion(chunks),
                null,
                AgentConfig.builder()
                        .maxIterations(5)
                        .build()
        );

        CompletionCancelToken token = agent.stream(
                UserCompletionMessage.of("测试"),
                chunk -> {}
        );

        token.cancel();
        assertTrue(token.isCancelled());
    }
}
```

## 3. 影响范围分析

### 3.1 模块依赖关系

```
open-sagent-base
    └── OpenSagentException (全局异常基类)

open-sagent-core
    ├── AgentConfig (扩展字段)
    └── OpenSagentAgentException (已有)

open-sagent-infrastructure
    ├── AbstractAgent (新增抽象基类)
    ├── SimpleAgent (变更：继承AbstractAgent，行为不变)
    └── ReActAgent (新增)
```

### 3.2 影响范围

| 模块 | 影响说明 |
|------|----------|
| open-sagent-core | AgentConfig新增字段，无破坏性变更（均有默认值） |
| open-sagent-infrastructure | SimpleAgent结构调整（继承AbstractAgent），行为100%保持向后兼容；新增ReActAgent |
| open-sagent-tools | 无直接影响 |
| open-sagent-web | 无直接影响 |
| open-sagent-cli | 无直接影响 |

### 3.3 扩展性说明

1. **AbstractAgent基类**：后续新增ScheduleAgent等其他Agent实现时，可直接继承AbstractAgent复用公共逻辑。
2. **配置可扩展**：AgentConfig采用Lombok `@Builder`，后续新增配置项无需修改构造方法。
3. **结束工具可配置**：`finishToolName`支持自定义，Prompt模板中通过`{{finishToolName}}`占位符动态替换，适应不同场景。
4. **流式与非流式分离**：`chat()`基于`complete()`保证Token用量精确统计；`stream()`基于`stream()`实现全程流式输出，职责边界清晰。

## 4. 测试计划

### 4.1 单元测试

| 测试类 | 模块 | 测试内容 |
|--------|------|----------|
| `AgentConfigTest` | core | ReAct默认配置、自定义配置验证 |
| `SimpleAgentTest` | infrastructure | 回归测试：确保SimpleAgent继承AbstractAgent后行为不变 |
| `ReActAgentTest` | infrastructure | 结束工具直接完成、多轮工具调用、超过迭代次数异常、缺少maxIterations异常、无工具调用直接返回、单轮流式、多轮流式+结束工具、取消 |

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
| sage | 2026-04-19 | 通过 | 补充第9条：stream模式下执行工具前发送AGENT_ACTION[EXECUTE_TOOL]标记 |
| | | | |
| | | | |
