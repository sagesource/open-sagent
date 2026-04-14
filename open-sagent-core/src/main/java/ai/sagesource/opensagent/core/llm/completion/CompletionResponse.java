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
