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
