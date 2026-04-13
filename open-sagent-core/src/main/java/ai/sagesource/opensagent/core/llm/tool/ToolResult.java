package ai.sagesource.opensagent.core.llm.tool;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Tool执行结果
 * <p>
 * 封装工具执行后的输出或错误信息
 *
 * @author: sage.xue
 * @time: 2026/4/13
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolResult {

    /**
     * 对应的工具调用ID
     */
    private String toolCallId;

    /**
     * 是否执行成功
     */
    @Builder.Default
    private boolean success = true;

    /**
     * 执行结果内容（文本）
     */
    private String content;

    /**
     * 错误信息（失败时）
     */
    private String errorMessage;

    /**
     * 快速创建成功结果
     *
     * @param toolCallId 调用ID
     * @param content    结果内容
     * @return ToolResult
     */
    public static ToolResult success(String toolCallId, String content) {
        return ToolResult.builder()
                .toolCallId(toolCallId)
                .success(true)
                .content(content)
                .build();
    }

    /**
     * 快速创建失败结果
     *
     * @param toolCallId   调用ID
     * @param errorMessage 错误信息
     * @return ToolResult
     */
    public static ToolResult failure(String toolCallId, String errorMessage) {
        return ToolResult.builder()
                .toolCallId(toolCallId)
                .success(false)
                .errorMessage(errorMessage)
                .build();
    }
}
