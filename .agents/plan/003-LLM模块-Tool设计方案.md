# 方案：003-LLM模块-Tool设计方案（变更版）

## 1. 背景与目的

### 1.1 背景

Open Sagent是一个AI-Agent框架，需要支持大语言模型（LLM）的Function Calling（工具调用）能力。根据项目架构设计方案 `project_design_llm.md`，Tool模块的职责划分如下：

- **Core模块**：负责Tool的**定义和执行**，包括数据模型、注解定义、注解解析生成`ToolDefinition`，以及工具注册、调度、执行框架。
- **Tools模块**：负责提供可复用的通用工具实现。
- **Infrastructure模块**：负责将`ToolDefinition`转换为各厂商SDK格式。

### 1.2 目的

1. Core模块保留并增强Tool执行框架，新增基于注解的反射调用能力
2. 建立`@Tool` + `@ToolParam`注解体系，支持一个类中定义多个工具
3. 保持向下兼容，已存在的`ToolDefinition`/`ToolCall`/`ToolResult`及执行框架位置不变
3. 建立`@Tool` + `@ToolParam`注解体系，支持一个类中定义多个工具
4. 保持向下兼容，已存在的`ToolDefinition`/`ToolCall`/`ToolResult`位置不变

## 2. 修改方案

### 2.1 模块职责边界

```
open-sagent-core (定义层 + 执行框架)
    ├── ToolDefinition / ToolCall / ToolResult / ToolParameterType
    ├── @Tool / @ToolParam (注解)
    ├── ToolMetadata (注解解析后的元数据)
    ├── ToolMetadataParser (注解解析器，生成ToolDefinition)
    ├── Tool (执行接口)
    ├── AnnotatedTool (基于注解反射的执行实现)
    ├── ToolRegistry (注册表)
    ├── ToolExecutor (执行器)
    ├── ToolUtils (便捷方法)
    ├── OpenSagentToolException
    └── AssistantCompletionMessage.toolCalls

open-sagent-tools (通用工具实现层)
    └── 提供可复用的通用工具实现
```

### 2.2 文件变更列表

#### open-sagent-core（保留 + 新增 + 修改）

| 文件路径 | 变更类型 | 说明 |
|----------|----------|------|
| `.../core/llm/tool/ToolParameterType.java` | 保留 | 参数类型枚举 |
| `.../core/llm/tool/ToolDefinition.java` | 保留 | Tool定义模型 |
| `.../core/llm/tool/ToolCall.java` | 保留 | LLM返回的Tool调用请求 |
| `.../core/llm/tool/ToolResult.java` | 保留 | Tool执行结果模型 |
| `.../core/llm/tool/Tool.java` | 保留 | 执行接口，位置不变 |
| `.../core/llm/tool/AbstractTool.java` | 保留 | 执行基类，位置不变 |
| `.../core/llm/tool/ToolRegistry.java` | 保留 | 注册表，位置不变 |
| `.../core/llm/tool/ToolExecutor.java` | 保留 | 执行器，位置不变 |
| `.../core/llm/tool/ToolUtils.java` | 保留 | 便捷方法，位置不变 |
| `.../core/llm/exception/OpenSagentToolException.java` | 保留 | Tool异常类 |
| `.../core/llm/message/AssistantCompletionMessage.java` | 保留 | toolCalls字段 |
| `.../core/llm/tool/annotation/Tool.java` | **新增** | 方法注解，定义工具名称和描述 |
| `.../core/llm/tool/annotation/ToolParam.java` | **新增** | 参数注解，定义参数名称、类型、描述、必填等 |
| `.../core/llm/tool/metadata/ToolMetadata.java` | **新增** | 封装单个注解方法的元数据 |
| `.../core/llm/tool/metadata/ToolParameterMetadata.java` | **新增** | 单个参数的元数据 |
| `.../core/llm/tool/parser/ToolMetadataParser.java` | **新增** | 解析类上的@Tool注解，生成List<ToolMetadata> |
| `.../core/llm/tool/AnnotatedTool.java` | **新增** | 基于注解+反射的执行实现 |
| `.../core/test/llm/tool/ToolDefinitionTest.java` | 修改 | 增加注解解析测试 |
| `.../core/test/llm/tool/AnnotatedToolTest.java` | **新增** | 注解执行测试 |

#### open-sagent-tools

无变更。

### 2.3 详细变更内容

#### 文件 1: `open-sagent-core/.../llm/tool/annotation/Tool.java`

```java
package ai.sagesource.opensagent.core.llm.tool.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 工具方法注解
 * <p>
 * 标注在方法上，用于定义一个可调用工具的元信息
 *
 * @author: sage.xue
 * @time: 2026/4/14
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Tool {

    /**
     * 工具名称
     */
    String name();

    /**
     * 工具描述
     */
    String description() default "";
}
```

#### 文件 2: `open-sagent-core/.../llm/tool/annotation/ToolParam.java`

```java
package ai.sagesource.opensagent.core.llm.tool.annotation;

import ai.sagesource.opensagent.core.llm.tool.ToolParameterType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 工具参数注解
 * <p>
 * 标注在方法参数上，定义参数在Tool Schema中的元信息
 *
 * @author: sage.xue
 * @time: 2026/4/14
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface ToolParam {

    /**
     * 参数名称（默认使用方法参数名，但编译后可能丢失，建议显式指定）
     */
    String name() default "";

    /**
     * 参数描述
     */
    String description() default "";

    /**
     * 参数类型（对应JSON Schema类型）
     */
    ToolParameterType type() default ToolParameterType.STRING;

    /**
     * 是否必填
     */
    boolean required() default true;

    /**
     * 枚举值（当参数为枚举类型时使用）
     */
    String[] enumValues() default {};
}
```

#### 文件 3: `open-sagent-core/.../llm/tool/metadata/ToolParameterMetadata.java`

```java
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
```

#### 文件 4: `open-sagent-core/.../llm/tool/metadata/ToolMetadata.java`

```java
package ai.sagesource.opensagent.core.llm.tool.metadata;

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
```

#### 文件 5: `open-sagent-core/.../llm/tool/parser/ToolMetadataParser.java`

```java
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
```

#### 文件 6: `open-sagent-core/.../test/llm/tool/ToolDefinitionTest.java`（修改后）

```java
package ai.sagesource.opensagent.core.llm.tool;

import ai.sagesource.opensagent.core.llm.tool.annotation.Tool;
import ai.sagesource.opensagent.core.llm.tool.annotation.ToolParam;
import ai.sagesource.opensagent.core.llm.tool.metadata.ToolMetadata;
import ai.sagesource.opensagent.core.llm.tool.parser.ToolMetadataParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tool模型与注解解析单元测试
 *
 * @author: sage.xue
 * @time: 2026/4/13
 */
class ToolDefinitionTest {

    @Test
    @DisplayName("创建ToolDefinition并生成Schema - 成功")
    void testToolDefinitionSchema() {
        ToolDefinition definition = ToolDefinition.builder()
                .name("get_weather")
                .description("获取指定城市的天气")
                .parameters(Map.of(
                        "city", Map.of("type", "string", "description", "城市名称"),
                        "unit", Map.of("type", "string", "description", "温度单位")
                ))
                .required(List.of("city"))
                .build();

        assertEquals("get_weather", definition.getName());
        Map<String, Object> schema = definition.toParameterSchema();
        assertEquals("object", schema.get("type"));
        assertTrue(schema.containsKey("properties"));
        assertTrue(schema.containsKey("required"));
    }

    @Test
    @DisplayName("创建ToolCall - 成功")
    void testToolCall() {
        ToolCall call = ToolCall.builder()
                .id("call_123")
                .name("get_weather")
                .arguments(Map.of("city", "北京"))
                .build();

        assertEquals("call_123", call.getId());
        assertEquals("get_weather", call.getName());
        assertEquals("北京", call.getArguments().get("city"));
    }

    @Test
    @DisplayName("创建ToolResult - 成功与失败")
    void testToolResult() {
        ToolResult success = ToolResult.success("call_123", "晴天 25°C");
        assertTrue(success.isSuccess());
        assertEquals("晴天 25°C", success.getContent());

        ToolResult failure = ToolResult.failure("call_123", "网络超时");
        assertFalse(failure.isSuccess());
        assertEquals("网络超时", failure.getErrorMessage());
    }

    @Test
    @DisplayName("解析@Tool注解 - 成功")
    void testToolAnnotationParser() {
        List<ToolMetadata> metadatas = ToolMetadataParser.parse(WeatherService.class);
        assertEquals(1, metadatas.size());

        ToolMetadata metadata = metadatas.get(0);
        assertEquals("get_weather", metadata.getDefinition().getName());
        assertEquals("获取天气", metadata.getDefinition().getDescription());
        assertTrue(metadata.getDefinition().getRequired().contains("city"));
    }

    // 测试用的内部类
    public static class WeatherService {
        @Tool(name = "get_weather", description = "获取天气")
        public String getWeather(
                @ToolParam(name = "city", description = "城市", type = ToolParameterType.STRING) String city,
                @ToolParam(name = "unit", description = "单位", type = ToolParameterType.STRING, required = false) String unit) {
            return city + ": 晴天";
        }
    }
}
```

#### 文件 7: `open-sagent-core/.../core/llm/tool/Tool.java`

**保留**（位置不变，无需修改）

#### 文件 8: `open-sagent-core/.../core/llm/tool/AnnotatedTool.java`

```java
package ai.sagesource.opensagent.core.llm.tool;

import ai.sagesource.opensagent.core.llm.exception.OpenSagentToolException;
import ai.sagesource.opensagent.core.llm.tool.ToolCall;
import ai.sagesource.opensagent.core.llm.tool.ToolDefinition;
import ai.sagesource.opensagent.core.llm.tool.ToolResult;
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
```

#### 文件 9: `open-sagent-core/.../core/llm/tool/ToolRegistry.java`

**保留**（位置不变，无需修改）

```java
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工具注册表
 * <p>
 * 管理所有可用工具的注册与发现
 *
 * @author: sage.xue
 * @time: 2026/4/14
 */
public class ToolRegistry {

    private final Map<String, Tool> tools = new LinkedHashMap<>();

    public void register(Tool tool) {
        if (tool == null || tool.getDefinition() == null) {
            throw new OpenSagentToolException("Tool或其Definition不能为空");
        }
        String name = tool.getDefinition().getName();
        if (tools.containsKey(name)) {
            throw new OpenSagentToolException("工具已存在: " + name);
        }
        tools.put(name, tool);
    }

    public void registerAll(List<Tool> tools) {
        if (tools != null) {
            for (Tool tool : tools) {
                register(tool);
            }
        }
    }

    public Tool get(String name) {
        Tool tool = tools.get(name);
        if (tool == null) {
            throw new OpenSagentToolException("工具不存在: " + name);
        }
        return tool;
    }

    public boolean contains(String name) {
        return tools.containsKey(name);
    }

    public List<ToolDefinition> getAllDefinitions() {
        List<ToolDefinition> definitions = new ArrayList<>();
        for (Tool tool : tools.values()) {
            definitions.add(tool.getDefinition());
        }
        return definitions;
    }

    public List<Tool> getAllTools() {
        return new ArrayList<>(tools.values());
    }

    public void clear() {
        tools.clear();
    }
}
```

#### 文件 10: `open-sagent-core/.../core/llm/tool/ToolExecutor.java`

**保留**（位置不变，无需修改）

#### 文件 11: `open-sagent-core/.../core/llm/tool/ToolUtils.java`

**保留**（位置不变，无需修改）

#### 文件 12: `open-sagent-core/.../test/llm/tool/AnnotatedToolTest.java`

```java
package ai.sagesource.opensagent.core.llm.tool;

import ai.sagesource.opensagent.core.llm.tool.ToolCall;
import ai.sagesource.opensagent.core.llm.tool.ToolDefinition;
import ai.sagesource.opensagent.core.llm.tool.ToolParameterType;
import ai.sagesource.opensagent.core.llm.tool.ToolResult;
import ai.sagesource.opensagent.core.llm.tool.annotation.Tool;
import ai.sagesource.opensagent.core.llm.tool.annotation.ToolParam;
import ai.sagesource.opensagent.core.llm.tool.metadata.ToolMetadata;
import ai.sagesource.opensagent.core.llm.tool.parser.ToolMetadataParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AnnotatedTool单元测试
 *
 * @author: sage.xue
 * @time: 2026/4/14
 */
class AnnotatedToolTest {

    @Test
    @DisplayName("通过反射执行注解工具 - 成功")
    void testAnnotatedToolExecute() {
        Calculator calculator = new Calculator();
        ToolMetadata metadata = ToolMetadataParser.parse(Calculator.class).get(0);
        AnnotatedTool tool = new AnnotatedTool(calculator, metadata);

        ToolDefinition definition = tool.getDefinition();
        assertEquals("add", definition.getName());

        ToolCall call = ToolCall.builder()
                .id("call_1")
                .name("add")
                .arguments(Map.of("a", 1, "b", 2))
                .build();

        ToolResult result = tool.execute(call);
        assertTrue(result.isSuccess());
        assertEquals("3", result.getContent());
    }

    public static class Calculator {
        @Tool(name = "add", description = "两数相加")
        public int add(
                @ToolParam(name = "a", description = "第一个数", type = ToolParameterType.INTEGER) int a,
                @ToolParam(name = "b", description = "第二个数", type = ToolParameterType.INTEGER) int b) {
            return a + b;
        }
    }
}
```

#### 文件 13: `open-sagent-core/.../test/llm/tool/ToolRegistryTest.java`

**保留**（位置不变，无需修改）

#### 文件 14: `open-sagent-core/.../test/llm/tool/ToolExecutorTest.java`

**保留**（位置不变，无需修改）

## 3. 影响范围分析

### 3.1 模块依赖关系

```
open-sagent-core (定义层 + 执行框架)
    ├── ToolDefinition / ToolCall / ToolResult / ToolParameterType
    ├── @Tool / @ToolParam
    ├── ToolMetadata / ToolParameterMetadata
    ├── ToolMetadataParser
    ├── Tool / AnnotatedTool
    ├── ToolRegistry / ToolExecutor / ToolUtils
    ├── OpenSagentToolException
    └── AssistantCompletionMessage

open-sagent-tools (通用工具实现层)
    └── 提供可复用的通用工具实现

open-sagent-infrastructure (适配层)
    └── 将 ToolDefinition 转换为厂商SDK格式
```

### 3.2 影响范围

| 模块 | 影响说明 |
|------|----------|
| open-sagent-core | 保留执行框架，新增注解和解析器及AnnotatedTool，模型类位置不变 |
| open-sagent-tools | 无变更 |
| open-sagent-infrastructure | 无直接影响 |
| open-sagent-web/cli/example | 无直接影响 |

### 3.3 扩展性说明

1. **新增注解工具**：开发者只需在类方法上标注`@Tool`和`@ToolParam`，通过`ToolMetadataParser.parse()`生成`ToolMetadata`，再包装为`AnnotatedTool`注册即可
2. **混合使用**：`ToolRegistry`和`ToolExecutor`基于`Tool`接口，可以同时注册`AnnotatedTool`和自定义的`Tool`实现
3. **厂商适配**：Infrastructure层负责将`ToolDefinition`转换为各厂商SDK格式

## 4. 测试计划

### 4.1 单元测试

| 测试类 | 模块 | 测试内容 |
|--------|------|----------|
| `ToolDefinitionTest` | core | 模型构建、Schema生成、注解解析 |
| `AnnotatedToolTest` | core | 反射执行、参数转换、成功/失败 |
| `ToolRegistryTest` | core | 注册、获取、重复注册、不存在异常 |
| `ToolExecutorTest` | core | 单条执行、批量执行、异常处理 |

### 4.2 编译验证

```bash
mvn clean compile test-compile -pl open-sagent-core -am
```

## 5. 兼容性说明

- `ToolDefinition`/`ToolCall`/`ToolResult` 位置不变，外部调用方不受影响
- `Tool` / `ToolRegistry` / `ToolExecutor` / `ToolUtils` 等执行框架类保留在原包 `ai.sagesource.opensagent.core.llm.tool`，无需迁移
- `open-sagent-tools` 已依赖 `open-sagent-core`，无需调整POM

## 6. 方案变更记录

### 变更 1（2026-04-14）：Tool 执行框架保留在 core 模块

**变更原因：**
经重新评估，Tool 执行框架（`Tool` 接口、`ToolRegistry`、`ToolExecutor`、`ToolUtils`）与数据模型和注解解析耦合度较高，保留在 `open-sagent-core` 中可减少模块间不必要的依赖跳迁，简化调用链路。`open-sagent-tools` 后续仅作为通用工具实现层。

**文件变更：**

| 文件路径 | 变更类型 | 说明 |
|----------|----------|------|
| `.../core/llm/tool/Tool.java` | 保留（原规划删除） | 执行接口不再迁移至 tools |
| `.../core/llm/tool/ToolRegistry.java` | 保留（原规划删除） | 注册表不再迁移至 tools |
| `.../core/llm/tool/ToolExecutor.java` | 保留（原规划删除） | 执行器不再迁移至 tools |
| `.../core/llm/tool/ToolUtils.java` | 保留（原规划删除） | 便捷方法不再迁移至 tools |
| `.../core/llm/tool/AnnotatedTool.java` | 新增（调整包路径） | 从 tools 调整至 core 的 `llm.tool` 包 |
| `.../core/test/llm/tool/AnnotatedToolTest.java` | 新增（调整包路径） | 从 tools 调整至 core 的测试包 |
| `.../tools/...` 下所有新增文件 | 取消新增 | 不再在 tools 模块新增任何文件 |

**关键代码变更：**
```java
// 修改前（变更版规划）
package ai.sagesource.opensagent.tools.tool; // AnnotatedTool 在 tools 模块

// 修改后（最终方案）
package ai.sagesource.opensagent.core.llm.tool; // AnnotatedTool 在 core 模块
```

## 7. 评审记录

| 评审人 | 时间 | 结论 | 备注 |
|--------|------|------|------|
| 项目Owner | 2026-04-14 | 通过 | Tool执行框架保留在core模块 |
