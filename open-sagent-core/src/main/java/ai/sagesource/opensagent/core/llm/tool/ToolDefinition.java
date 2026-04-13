package ai.sagesource.opensagent.core.llm.tool;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tool定义模型
 * <p>
 * 描述一个可调用工具的名称、描述和参数Schema（JSON Schema风格）
 *
 * @author: sage.xue
 * @time: 2026/4/13
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolDefinition {

    /**
     * 工具名称
     */
    private String name;

    /**
     * 工具描述
     */
    private String description;

    /**
     * 参数Schema（JSON Schema风格的properties）
     * <p>
     * Key为参数名，Value为参数描述Map（包含type、description、enum等）
     */
    @Builder.Default
    private Map<String, Object> parameters = new LinkedHashMap<>();

    /**
     * 必填参数列表
     */
    @Builder.Default
    private List<String> required = new ArrayList<>();

    /**
     * 构建标准JSON Schema格式的parameters对象
     *
     * @return JSON Schema Map
     */
    public Map<String, Object> toParameterSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", parameters);
        if (required != null && !required.isEmpty()) {
            schema.put("required", required);
        }
        return schema;
    }
}
