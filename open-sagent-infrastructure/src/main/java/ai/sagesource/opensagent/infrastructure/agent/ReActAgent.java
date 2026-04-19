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

    /**
     * 工具执行标记，stream模式下发送给客户端用于识别工具执行状态
     */
    private static final String AGENT_ACTION_EXECUTE_TOOL = "AGENT_ACTION[EXECUTE_TOOL]";

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
            StringBuilder roundTextBuilder = new StringBuilder();
            AtomicBoolean roundFinished = new AtomicBoolean(false);

            Consumer<StreamChunk> wrappedConsumer = chunk -> {
                if (cancelled.get()) {
                    return;
                }

                // 收集本轮文本
                if (chunk.getDeltaText() != null) {
                    roundTextBuilder.append(chunk.getDeltaText());
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

            // 将本轮Assistant文本保存到Memory
            if (memory != null && roundTextBuilder.length() > 0) {
                memory.addMessage(AssistantCompletionMessage.of(roundTextBuilder.toString()));
            }

            // 本轮无工具调用，正常结束
            if (roundToolCalls.isEmpty()) {
                break;
            }

            // 发送工具执行标记，供客户端识别展示
            consumer.accept(StreamChunk.builder()
                    .deltaText(AGENT_ACTION_EXECUTE_TOOL)
                    .aggregatedText(AGENT_ACTION_EXECUTE_TOOL)
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
