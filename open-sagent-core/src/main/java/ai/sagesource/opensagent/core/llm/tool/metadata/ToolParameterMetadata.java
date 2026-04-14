package ai.sagesource.opensagent.core.llm.tool.metadata;

import ai.sagesource.opensagent.core.llm.tool.ToolParameterType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工具参数元数据
 *
 * @author: sage.xue
 * @time: 2026/4/14
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolParameterMetadata {

    /**
     * 参数在Schema中的名称
     */
    private String name;

    /**
     * 参数描述
     */
    private String description;

    /**
     * 参数类型
     */
    private ToolParameterType type;

    /**
     * 是否必填
     */
    private boolean required;

    /**
     * 枚举值
     */
    private String[] enumValues;

    /**
     * 方法参数类型（用于执行时类型转换）
     */
    private Class<?> parameterType;
}
