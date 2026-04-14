package ai.sagesource.opensagent.core.llm.tool.parser;

import ai.sagesource.opensagent.core.llm.exception.OpenSagentToolException;
import ai.sagesource.opensagent.core.llm.tool.ToolDefinition;
import ai.sagesource.opensagent.core.llm.tool.ToolParameterType;
import ai.sagesource.opensagent.core.llm.tool.annotation.Tool;
import ai.sagesource.opensagent.core.llm.tool.annotation.ToolParam;
import ai.sagesource.opensagent.core.llm.tool.metadata.ToolMetadata;
import ai.sagesource.opensagent.core.llm.tool.metadata.ToolParameterMetadata;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.*;

/**
 * Tool注解解析器
 * <p>
 * 扫描类上的@Tool注解方法，生成ToolMetadata列表
 *
 * @author: sage.xue
 * @time: 2026/4/14
 */
public class ToolMetadataParser {

    /**
     * 解析指定类中的所有@Tool方法
     *
     * @param clazz 目标类
     * @return ToolMetadata列表
     */
    public static List<ToolMetadata> parse(Class<?> clazz) {
        if (clazz == null) {
            throw new OpenSagentToolException("解析Tool注解时，Class不能为空");
        }
        List<ToolMetadata> metadatas = new ArrayList<>();
        for (Method method : clazz.getDeclaredMethods()) {
            Tool toolAnno = method.getAnnotation(Tool.class);
            if (toolAnno == null) {
                continue;
            }
            ToolMetadata metadata = parseMethod(clazz, method, toolAnno);
            metadatas.add(metadata);
        }
        return metadatas;
    }

    private static ToolMetadata parseMethod(Class<?> clazz, Method method, Tool toolAnno) {
        String toolName = toolAnno.name();
        if (toolName == null || toolName.isEmpty()) {
            throw new OpenSagentToolException("@Tool的name不能为空，方法: " + method.getName());
        }

        Map<String, Object> properties = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();
        List<ToolParameterMetadata> paramMetadatas = new ArrayList<>();

        Parameter[] parameters = method.getParameters();
        for (int i = 0; i < parameters.length; i++) {
            Parameter param = parameters[i];
            ToolParam paramAnno = param.getAnnotation(ToolParam.class);

            String paramName = paramAnno != null && !paramAnno.name().isEmpty()
                    ? paramAnno.name() : param.getName();
            // 如果编译后参数名丢失，使用arg0, arg1等，此时必须显式指定name
            if (paramName == null || paramName.startsWith("arg")) {
                throw new OpenSagentToolException(
                        "方法 " + method.getName() + " 的参数 " + i + " 必须显式指定@ToolParam的name");
            }

            ToolParameterType type = paramAnno != null ? paramAnno.type() : mapType(param.getType());
            String description = paramAnno != null ? paramAnno.description() : "";
            boolean isRequired = paramAnno == null || paramAnno.required();
            String[] enumValues = paramAnno != null ? paramAnno.enumValues() : new String[0];

            Map<String, Object> prop = new LinkedHashMap<>();
            prop.put("type", type.name().toLowerCase());
            if (!description.isEmpty()) {
                prop.put("description", description);
            }
            if (enumValues != null && enumValues.length > 0) {
                prop.put("enum", Arrays.asList(enumValues));
            }
            properties.put(paramName, prop);

            if (isRequired) {
                required.add(paramName);
            }

            paramMetadatas.add(ToolParameterMetadata.builder()
                    .name(paramName)
                    .description(description)
                    .type(type)
                    .required(isRequired)
                    .enumValues(enumValues)
                    .parameterType(param.getType())
                    .build());
        }

        ToolDefinition definition = ToolDefinition.builder()
                .name(toolName)
                .description(toolAnno.description())
                .parameters(properties)
                .required(required)
                .build();

        return ToolMetadata.builder()
                .definition(definition)
                .targetClass(clazz)
                .method(method)
                .parameters(paramMetadatas)
                .build();
    }

    private static ToolParameterType mapType(Class<?> type) {
        if (type == String.class) return ToolParameterType.STRING;
        if (type == int.class || type == Integer.class
                || type == long.class || type == Long.class
                || type == short.class || type == Short.class
                || type == byte.class || type == Byte.class) {
            return ToolParameterType.INTEGER;
        }
        if (type == double.class || type == Double.class
                || type == float.class || type == Float.class) {
            return ToolParameterType.NUMBER;
        }
        if (type == boolean.class || type == Boolean.class) return ToolParameterType.BOOLEAN;
        if (type.isArray() || List.class.isAssignableFrom(type)) return ToolParameterType.ARRAY;
        return ToolParameterType.OBJECT;
    }
}
