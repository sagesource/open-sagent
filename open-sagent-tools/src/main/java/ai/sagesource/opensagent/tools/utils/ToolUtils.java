package ai.sagesource.opensagent.tools.utils;

import ai.sagesource.opensagent.core.llm.tool.ToolDefinition;
import ai.sagesource.opensagent.core.llm.tool.ToolParameterType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工具便捷方法
 *
 * @author: sage.xue
 * @time: 2026/4/13
 */
public final class ToolUtils {

    private ToolUtils() {
    }

    /**
     * 构建参数属性描述
     *
     * @param type        参数类型
     * @param description 参数描述
     * @return 属性Map
     */
    public static Map<String, Object> property(ToolParameterType type, String description) {
        Map<String, Object> prop = new LinkedHashMap<>();
        prop.put("type", type.name().toLowerCase());
        prop.put("description", description);
        return prop;
    }

    /**
     * 构建带枚举值的参数属性描述
     *
     * @param type        参数类型
     * @param description 参数描述
     * @param enumValues  枚举值列表
     * @return 属性Map
     */
    public static Map<String, Object> property(ToolParameterType type, String description, String... enumValues) {
        Map<String, Object> prop = property(type, description);
        if (enumValues != null && enumValues.length > 0) {
            prop.put("enum", Arrays.asList(enumValues));
        }
        return prop;
    }

    /**
     * 将ToolDefinition列表转换为OpenAI风格的functions列表
     *
     * @param definitions ToolDefinition列表
     * @return functions列表（Map格式）
     */
    public static List<Map<String, Object>> toFunctions(List<ToolDefinition> definitions) {
        List<Map<String, Object>> functions = new ArrayList<>();
        if (definitions == null) {
            return functions;
        }
        for (ToolDefinition def : definitions) {
            Map<String, Object> func = new LinkedHashMap<>();
            func.put("type", "function");
            Map<String, Object> function = new LinkedHashMap<>();
            function.put("name", def.getName());
            function.put("description", def.getDescription());
            function.put("parameters", def.toParameterSchema());
            func.put("function", function);
            functions.add(func);
        }
        return functions;
    }
}
