package ai.sagesource.opensagent.infrastructure.llm.tool.metadata;

import ai.sagesource.opensagent.core.llm.tool.ToolDefinition;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.lang.reflect.Method;
import java.util.List;

/**
 * 工具方法元数据
 * <p>
 * 封装一个被@Tool注解标记的方法的定义信息，
 * 包含ToolDefinition以及执行所需的反射信息
 *
 * @author: sage.xue
 * @time: 2026/4/14
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolMetadata {

    /**
     * Tool定义（用于向LLM注册）
     */
    private ToolDefinition definition;

    /**
     * 方法所在类
     */
    private Class<?> targetClass;

    /**
     * 方法对象
     */
    private Method method;

    /**
     * 参数元数据列表（按方法参数顺序）
     */
    private List<ToolParameterMetadata> parameters;
}
