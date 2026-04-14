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
