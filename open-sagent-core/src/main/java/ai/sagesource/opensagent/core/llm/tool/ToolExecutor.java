package ai.sagesource.opensagent.core.llm.tool;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * 工具执行器
 * <p>
 * 根据LLM返回的ToolCall列表，调度并执行对应的工具
 *
 * @author: sage.xue
 * @time: 2026/4/13
 */
@Slf4j
public class ToolExecutor {

    private final ToolRegistry registry;

    public ToolExecutor(ToolRegistry registry) {
        this.registry = registry;
    }

    /**
     * 执行单个ToolCall
     *
     * @param toolCall 工具调用请求
     * @return 执行结果
     */
    public ToolResult execute(ToolCall toolCall) {
        if (toolCall == null || toolCall.getName() == null) {
            return ToolResult.failure(
                    toolCall != null ? toolCall.getId() : null,
                    "ToolCall或工具名称不能为空");
        }
        Tool tool = registry.get(toolCall.getName());
        return tool.execute(toolCall);
    }

    /**
     * 批量执行ToolCall
     *
     * @param toolCalls 工具调用请求列表
     * @return 执行结果列表
     */
    public List<ToolResult> executeAll(List<ToolCall> toolCalls) {
        List<ToolResult> results = new ArrayList<>();
        if (toolCalls == null || toolCalls.isEmpty()) {
            return results;
        }
        for (ToolCall toolCall : toolCalls) {
            try {
                results.add(execute(toolCall));
            } catch (Exception e) {
                log.error("> ToolExecutor | 批量执行工具失败: {} <", toolCall.getName(), e);
                results.add(ToolResult.failure(
                        toolCall.getId(),
                        "执行异常: " + e.getMessage()));
            }
        }
        return results;
    }
}
