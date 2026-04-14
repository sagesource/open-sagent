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
