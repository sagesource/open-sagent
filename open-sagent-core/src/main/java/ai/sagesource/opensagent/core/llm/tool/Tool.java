package ai.sagesource.opensagent.core.llm.tool;

/**
 * Tool执行接口
 * <p>
 * 所有可调用工具必须实现此接口，提供定义和执行能力
 *
 * @author: sage.xue
 * @time: 2026/4/13
 */
public interface Tool {

    /**
     * 获取Tool定义（用于向LLM注册）
     *
     * @return ToolDefinition
     */
    ToolDefinition getDefinition();

    /**
     * 执行Tool调用
     *
     * @param toolCall 工具调用请求
     * @return ToolResult 执行结果
     */
    ToolResult execute(ToolCall toolCall);
}
