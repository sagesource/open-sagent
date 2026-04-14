package ai.sagesource.opensagent.core.llm.tool;

import ai.sagesource.opensagent.core.llm.exception.OpenSagentToolException;
import ai.sagesource.opensagent.core.llm.tool.metadata.ToolMetadata;
import ai.sagesource.opensagent.core.llm.tool.metadata.ToolParameterMetadata;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Map;

/**
 * 基于注解的Tool执行实现
 * <p>
 * 通过反射调用被@Tool注解的方法，执行实际的工具逻辑
 *
 * @author: sage.xue
 * @time: 2026/4/14
 */
@Slf4j
public class AnnotatedTool implements Tool {

    private final Object target;
    private final ToolMetadata metadata;

    public AnnotatedTool(Object target, ToolMetadata metadata) {
        if (target == null) {
            throw new OpenSagentToolException("AnnotatedTool的目标实例不能为空");
        }
        if (metadata == null) {
            throw new OpenSagentToolException("ToolMetadata不能为空");
        }
        this.target = target;
        this.metadata = metadata;
    }

    @Override
    public ToolDefinition getDefinition() {
        return metadata.getDefinition();
    }

    @Override
    public ToolResult execute(ToolCall toolCall) {
        if (toolCall == null) {
            throw new OpenSagentToolException("ToolCall不能为空");
        }
        if (!metadata.getDefinition().getName().equals(toolCall.getName())) {
            throw new OpenSagentToolException(
                    "工具名称不匹配，期望: " + metadata.getDefinition().getName() + ", 实际: " + toolCall.getName());
        }
        try {
            log.debug("开始执行注解工具: {}, callId: {}", metadata.getDefinition().getName(), toolCall.getId());
            Object[] args = buildArguments(toolCall.getArguments());
            Object result = metadata.getMethod().invoke(target, args);
            log.debug("注解工具执行完成: {}, callId: {}", metadata.getDefinition().getName(), toolCall.getId());
            return ToolResult.success(toolCall.getId(), result != null ? result.toString() : "");
        } catch (InvocationTargetException e) {
            Throwable cause = e.getTargetException() != null ? e.getTargetException() : e;
            log.error("注解工具执行失败: {}, callId: {}, error: {}",
                    metadata.getDefinition().getName(), toolCall.getId(), cause.getMessage(), cause);
            return ToolResult.failure(toolCall.getId(),
                    "工具执行失败: " + (cause.getMessage() != null ? cause.getMessage() : cause.getClass().getName()));
        } catch (Exception e) {
            log.error("注解工具执行失败: {}, callId: {}, error: {}",
                    metadata.getDefinition().getName(), toolCall.getId(), e.getMessage(), e);
            return ToolResult.failure(toolCall.getId(),
                    "工具执行失败: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getName()));
        }
    }

    private Object[] buildArguments(Map<String, Object> arguments) {
        List<ToolParameterMetadata> params = metadata.getParameters();
        Object[] args = new Object[params.size()];
        for (int i = 0; i < params.size(); i++) {
            ToolParameterMetadata param = params.get(i);
            Object value = arguments != null ? arguments.get(param.getName()) : null;
            args[i] = convertValue(value, param.getParameterType());
        }
        return args;
    }

    private Object convertValue(Object value, Class<?> targetType) {
        if (value == null) {
            return null;
        }
        if (targetType.isInstance(value)) {
            return value;
        }
        if (targetType == String.class) {
            return value.toString();
        }
        if (targetType == int.class || targetType == Integer.class) {
            return Integer.parseInt(value.toString());
        }
        if (targetType == long.class || targetType == Long.class) {
            return Long.parseLong(value.toString());
        }
        if (targetType == double.class || targetType == Double.class) {
            return Double.parseDouble(value.toString());
        }
        if (targetType == boolean.class || targetType == Boolean.class) {
            return Boolean.parseBoolean(value.toString());
        }
        return value;
    }
}
