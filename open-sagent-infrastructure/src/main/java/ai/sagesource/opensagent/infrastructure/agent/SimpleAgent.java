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
