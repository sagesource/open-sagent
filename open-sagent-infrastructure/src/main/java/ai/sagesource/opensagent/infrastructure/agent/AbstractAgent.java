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
