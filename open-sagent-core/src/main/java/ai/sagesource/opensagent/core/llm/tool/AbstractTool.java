package ai.sagesource.opensagent.core.llm.tool;

import ai.sagesource.opensagent.core.llm.exception.OpenSagentToolException;
import lombok.extern.slf4j.Slf4j;

/**
 * Tool抽象基类
 * <p>
 * 提供通用的定义缓存和参数校验逻辑
 *
 * @author: sage.xue
 * @time: 2026/4/13
 */
@Slf4j
public abstract class AbstractTool implements Tool {

    private final ToolDefinition definition;

    protected AbstractTool(ToolDefinition definition) {
        if (definition == null || definition.getName() == null || definition.getName().isEmpty()) {
            throw new OpenSagentToolException("ToolDefinition及其name不能为空");
        }
        this.definition = definition;
    }

    @Override
    public ToolDefinition getDefinition() {
        return definition;
    }

    @Override
    public final ToolResult execute(ToolCall toolCall) {
        if (toolCall == null) {
            throw new OpenSagentToolException("ToolCall不能为空");
        }
        if (!definition.getName().equals(toolCall.getName())) {
            throw new OpenSagentToolException(
                    "工具名称不匹配，期望: " + definition.getName() + ", 实际: " + toolCall.getName());
        }
        try {
            log.debug("开始执行工具: {}, callId: {}", definition.getName(), toolCall.getId());
            ToolResult result = doExecute(toolCall);
            log.debug("工具执行完成: {}, callId: {}", definition.getName(), toolCall.getId());
            return result;
        } catch (Exception e) {
            log.error("工具执行失败: {}, callId: {}, error: {}",
                    definition.getName(), toolCall.getId(), e.getMessage(), e);
            return ToolResult.failure(toolCall.getId(),
                    "工具执行失败: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getName()));
        }
    }

    /**
     * 子类实现具体的执行逻辑
     *
     * @param toolCall 工具调用请求
     * @return 执行结果
     */
    protected abstract ToolResult doExecute(ToolCall toolCall);
}
