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
            List<CompletionMessage> completionMessages = new ArrayList<>(messages);
            memory.addMessages(completionMessages);
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
