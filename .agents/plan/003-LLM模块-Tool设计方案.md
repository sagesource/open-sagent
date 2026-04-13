# 方案：003-LLM模块-Tool设计方案

## 1. 背景与目的

### 1.1 背景

Open Sagent是一个AI-Agent框架，需要支持大语言模型（LLM）的Function Calling（工具调用）能力。为了屏蔽不同厂商（OpenAI、Anthropic、Azure等）在Function Calling API上的差异，需要在Core模块中定义统一的Tool抽象模型，并在Tools模块中提供工具注册、发现与执行的基础框架。

目前Core模块已存在 `ToolCompletionMessage`（工具结果消息），但缺少Tool定义、Tool调用请求等核心模型，且 `AssistantCompletionMessage` 尚未支持携带 `toolCalls`。

### 1.2 目的

1. 设计统一的Tool定义模型，支持JSON Schema风格的参数描述
2. 定义Tool调用请求（ToolCall）和Tool执行结果（ToolResult）模型
3. 扩展Assistant消息以支持携带ToolCalls
4. 在Tools模块提供工具注册表（ToolRegistry）和执行框架（ToolExecutor）
5. 建立Tool模块异常处理机制

## 2. 修改方案

### 2.1 文件变更列表

| 文件路径 | 变更类型 | 说明 |
|----------|----------|------|
| `open-sagent-core/src/main/java/ai/sagesource/opensagent/core/llm/tool/ToolDefinition.java` | 新增 | Tool定义模型（名称、描述、参数Schema） |
| `open-sagent-core/src/main/java/ai/sagesource/opensagent/core/llm/tool/ToolCall.java` | 新增 | LLM返回的Tool调用请求 |
| `open-sagent-core/src/main/java/ai/sagesource/opensagent/core/llm/tool/ToolResult.java` | 新增 | Tool执行结果模型 |
| `open-sagent-core/src/main/java/ai/sagesource/opensagent/core/llm/tool/ToolParameterType.java` | 新增 | 参数类型枚举（STRING/INTEGER/NUMBER/BOOLEAN/OBJECT/ARRAY） |
| `open-sagent-core/src/main/java/ai/sagesource/opensagent/core/llm/exception/OpenSagentToolException.java` | 新增 | Tool模块异常类 |
| `open-sagent-core/src/main/java/ai/sagesource/opensagent/core/llm/message/AssistantCompletionMessage.java` | 修改 | 新增 `toolCalls` 字段 |
| `open-sagent-tools/src/main/java/ai/sagesource/opensagent/tools/tool/Tool.java` | 新增 | Tool执行接口 |
| `open-sagent-tools/src/main/java/ai/sagesource/opensagent/tools/tool/AbstractTool.java` | 新增 | Tool抽象基类 |
| `open-sagent-tools/src/main/java/ai/sagesource/opensagent/tools/registry/ToolRegistry.java` | 新增 | 工具注册表 |
| `open-sagent-tools/src/main/java/ai/sagesource/opensagent/tools/executor/ToolExecutor.java` | 新增 | 工具执行器 |
| `open-sagent-tools/src/main/java/ai/sagesource/opensagent/tools/utils/ToolUtils.java` | 新增 | 工具便捷方法 |
| `open-sagent-core/src/test/java/ai/sagesource/opensagent/core/llm/tool/ToolDefinitionTest.java` | 新增 | Tool模型单元测试 |
| `open-sagent-tools/src/test/java/ai/sagesource/opensagent/tools/registry/ToolRegistryTest.java` | 新增 | 注册表单元测试 |
| `open-sagent-tools/src/test/java/ai/sagesource/opensagent/tools/executor/ToolExecutorTest.java` | 新增 | 执行器单元测试 |

### 2.2 详细变更内容

#### 文件 1: `open-sagent-core/src/main/java/ai/sagesource/opensagent/core/llm/tool/ToolParameterType.java`

```java
package ai.sagesource.opensagent.core.llm.tool;

/**
 * 工具参数类型枚举
 * <p>
 * 对应JSON Schema的基础类型，用于描述Tool参数的Schema
 *
 * @author: sage.xue
 * @time: 2026/4/13
 */
public enum ToolParameterType {

    STRING,
    INTEGER,
    NUMBER,
    BOOLEAN,
    OBJECT,
    ARRAY
}
```

#### 文件 2: `open-sagent-core/src/main/java/ai/sagesource/opensagent/core/llm/tool/ToolDefinition.java`

```java
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
```

#### 文件 3: `open-sagent-core/src/main/java/ai/sagesource/opensagent/core/llm/tool/ToolCall.java`

```java
package ai.sagesource.opensagent.core.llm.tool;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Tool调用请求
 * <p>
 * LLM返回的待执行工具调用信息，包含调用ID、工具名称和参数
 *
 * @author: sage.xue
 * @time: 2026/4/13
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolCall {

    /**
     * 工具调用唯一ID
     */
    private String id;

    /**
     * 工具名称
     */
    private String name;

    /**
     * 调用参数（JSON解析后的Map）
     */
    private Map<String, Object> arguments;
}
```

#### 文件 4: `open-sagent-core/src/main/java/ai/sagesource/opensagent/core/llm/tool/ToolResult.java`

```java
package ai.sagesource.opensagent.core.llm.tool;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Tool执行结果
 * <p>
 * 封装工具执行后的输出或错误信息
 *
 * @author: sage.xue
 * @time: 2026/4/13
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolResult {

    /**
     * 对应的工具调用ID
     */
    private String toolCallId;

    /**
     * 是否执行成功
     */
    @Builder.Default
    private boolean success = true;

    /**
     * 执行结果内容（文本）
     */
    private String content;

    /**
     * 错误信息（失败时）
     */
    private String errorMessage;

    /**
     * 快速创建成功结果
     *
     * @param toolCallId 调用ID
     * @param content    结果内容
     * @return ToolResult
     */
    public static ToolResult success(String toolCallId, String content) {
        return ToolResult.builder()
                .toolCallId(toolCallId)
                .success(true)
                .content(content)
                .build();
    }

    /**
     * 快速创建失败结果
     *
     * @param toolCallId   调用ID
     * @param errorMessage 错误信息
     * @return ToolResult
     */
    public static ToolResult failure(String toolCallId, String errorMessage) {
        return ToolResult.builder()
                .toolCallId(toolCallId)
                .success(false)
                .errorMessage(errorMessage)
                .build();
    }
}
```

#### 文件 5: `open-sagent-core/src/main/java/ai/sagesource/opensagent/core/llm/exception/OpenSagentToolException.java`

```java
package ai.sagesource.opensagent.core.llm.exception;

import ai.sagesource.opensagent.base.exception.OpenSagentException;

/**
 * Tool模块异常类
 *
 * @author: sage.xue
 * @time: 2026/4/13
 */
public class OpenSagentToolException extends OpenSagentException {

    public OpenSagentToolException(String message) {
        super(message);
    }

    public OpenSagentToolException(String message, Throwable cause) {
        super(message, cause);
    }

    public OpenSagentToolException(Throwable cause) {
        super(cause);
    }
}
```

#### 文件 6: `open-sagent-core/src/main/java/ai/sagesource/opensagent/core/llm/message/AssistantCompletionMessage.java`

```java
package ai.sagesource.opensagent.core.llm.message;

import ai.sagesource.opensagent.core.llm.tool.ToolCall;
import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 助手消息实现
 * <p>
 * 表示AI助手（大模型）的回复消息
 *
 * @author: sage.xue
 * @time: 2026/4/13
 */
@Data
@Builder
public class AssistantCompletionMessage implements CompletionMessage {

    /**
     * 消息ID
     */
    private String messageId;

    /**
     * 消息内容列表
     */
    @Builder.Default
    private List<MessageContent> contents = new ArrayList<>();

    /**
     * 是否完成思考（适用于有推理过程的模型）
     */
    @Builder.Default
    private boolean reasoningComplete = true;

    /**
     * 推理内容（可选）
     */
    private String reasoningContent;

    /**
     * 工具调用列表（当模型返回function calling时）
     */
    @Builder.Default
    private List<ToolCall> toolCalls = new ArrayList<>();

    @Override
    public MessageRole getRole() {
        return MessageRole.ASSISTANT;
    }

    @Override
    public List<MessageContent> getContents() {
        return contents;
    }

    @Override
    public String getMessageId() {
        return messageId;
    }

    @Override
    public void addContent(MessageContent content) {
        if (this.contents == null) {
            this.contents = new ArrayList<>();
        }
        this.contents.add(content);
    }

    /**
     * 创建纯文本助手消息（便捷方法）
     *
     * @param text 回复文本
     * @return AssistantCompletionMessage实例
     */
    public static AssistantCompletionMessage of(String text) {
        return AssistantCompletionMessage.builder()
                .contents(new ArrayList<>(List.of(TextContent.builder().text(text).build())))
                .build();
    }
}
```

#### 文件 7: `open-sagent-tools/src/main/java/ai/sagesource/opensagent/tools/tool/Tool.java`

```java
package ai.sagesource.opensagent.tools.tool;

import ai.sagesource.opensagent.core.llm.tool.ToolCall;
import ai.sagesource.opensagent.core.llm.tool.ToolDefinition;
import ai.sagesource.opensagent.core.llm.tool.ToolResult;

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
```

#### 文件 8: `open-sagent-tools/src/main/java/ai/sagesource/opensagent/tools/tool/AbstractTool.java`

```java
package ai.sagesource.opensagent.tools.tool;

import ai.sagesource.opensagent.core.llm.tool.ToolCall;
import ai.sagesource.opensagent.core.llm.tool.ToolDefinition;
import ai.sagesource.opensagent.core.llm.tool.ToolResult;
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
```

#### 文件 9: `open-sagent-tools/src/main/java/ai/sagesource/opensagent/tools/registry/ToolRegistry.java`

```java
package ai.sagesource.opensagent.tools.registry;

import ai.sagesource.opensagent.core.llm.tool.ToolDefinition;
import ai.sagesource.opensagent.core.llm.exception.OpenSagentToolException;
import ai.sagesource.opensagent.tools.tool.Tool;

import java.util.*;

/**
 * 工具注册表
 * <p>
 * 管理所有可用工具的注册与发现
 *
 * @author: sage.xue
 * @time: 2026/4/13
 */
public class ToolRegistry {

    private final Map<String, Tool> tools = new LinkedHashMap<>();

    /**
     * 注册工具
     *
     * @param tool 工具实例
     */
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

    /**
     * 批量注册工具
     *
     * @param tools 工具实例列表
     */
    public void registerAll(List<Tool> tools) {
        if (tools != null) {
            for (Tool tool : tools) {
                register(tool);
            }
        }
    }

    /**
     * 根据名称获取工具
     *
     * @param name 工具名称
     * @return Tool实例
     * @throws OpenSagentToolException 工具不存在时抛出
     */
    public Tool get(String name) {
        Tool tool = tools.get(name);
        if (tool == null) {
            throw new OpenSagentToolException("工具不存在: " + name);
        }
        return tool;
    }

    /**
     * 检查工具是否存在
     *
     * @param name 工具名称
     * @return true表示存在
     */
    public boolean contains(String name) {
        return tools.containsKey(name);
    }

    /**
     * 获取所有工具定义
     *
     * @return ToolDefinition列表
     */
    public List<ToolDefinition> getAllDefinitions() {
        List<ToolDefinition> definitions = new ArrayList<>();
        for (Tool tool : tools.values()) {
            definitions.add(tool.getDefinition());
        }
        return definitions;
    }

    /**
     * 获取已注册的所有工具
     *
     * @return Tool列表
     */
    public List<Tool> getAllTools() {
        return new ArrayList<>(tools.values());
    }

    /**
     * 清空注册表
     */
    public void clear() {
        tools.clear();
    }
}
```

#### 文件 10: `open-sagent-tools/src/main/java/ai/sagesource/opensagent/tools/executor/ToolExecutor.java`

```java
package ai.sagesource.opensagent.tools.executor;

import ai.sagesource.opensagent.core.llm.tool.ToolCall;
import ai.sagesource.opensagent.core.llm.tool.ToolResult;
import ai.sagesource.opensagent.tools.registry.ToolRegistry;
import ai.sagesource.opensagent.tools.tool.Tool;
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
                log.error("批量执行工具失败: {}", toolCall.getName(), e);
                results.add(ToolResult.failure(
                        toolCall.getId(),
                        "执行异常: " + e.getMessage()));
            }
        }
        return results;
    }
}
```

#### 文件 11: `open-sagent-tools/src/main/java/ai/sagesource/opensagent/tools/utils/ToolUtils.java`

```java
package ai.sagesource.opensagent.tools.utils;

import ai.sagesource.opensagent.core.llm.tool.ToolDefinition;
import ai.sagesource.opensagent.core.llm.tool.ToolParameterType;

import java.util.LinkedHashMap;
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
            prop.put("enum", java.util.Arrays.asList(enumValues));
        }
        return prop;
    }

    /**
     * 将ToolDefinition列表转换为OpenAI风格的functions列表
     *
     * @param definitions ToolDefinition列表
     * @return functions列表（Map格式）
     */
    public static java.util.List<Map<String, Object>> toFunctions(java.util.List<ToolDefinition> definitions) {
        java.util.List<Map<String, Object>> functions = new java.util.ArrayList<>();
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
```

#### 文件 12: `open-sagent-core/src/test/java/ai/sagesource/opensagent/core/llm/tool/ToolDefinitionTest.java`

```java
package ai.sagesource.opensagent.core.llm.tool;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tool模型单元测试
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
}
```

#### 文件 13: `open-sagent-tools/src/test/java/ai/sagesource/opensagent/tools/registry/ToolRegistryTest.java`

```java
package ai.sagesource.opensagent.tools.registry;

import ai.sagesource.opensagent.core.llm.tool.ToolDefinition;
import ai.sagesource.opensagent.core.llm.exception.OpenSagentToolException;
import ai.sagesource.opensagent.tools.tool.AbstractTool;
import ai.sagesource.opensagent.tools.tool.Tool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 工具注册表单元测试
 *
 * @author: sage.xue
 * @time: 2026/4/13
 */
class ToolRegistryTest {

    private ToolRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new ToolRegistry();
    }

    @Test
    @DisplayName("注册和获取工具 - 成功")
    void testRegisterAndGet() {
        Tool tool = createTestTool("test_tool", "测试工具");
        registry.register(tool);

        assertTrue(registry.contains("test_tool"));
        assertEquals(tool, registry.get("test_tool"));
        assertEquals(1, registry.getAllDefinitions().size());
    }

    @Test
    @DisplayName("重复注册工具 - 抛出异常")
    void testDuplicateRegister() {
        Tool tool = createTestTool("test_tool", "测试工具");
        registry.register(tool);

        assertThrows(OpenSagentToolException.class, () -> registry.register(tool));
    }

    @Test
    @DisplayName("获取不存在的工具 - 抛出异常")
    void testGetNotExist() {
        assertThrows(OpenSagentToolException.class, () -> registry.get("not_exist"));
    }

    private Tool createTestTool(String name, String description) {
        ToolDefinition definition = ToolDefinition.builder()
                .name(name)
                .description(description)
                .build();
        return new AbstractTool(definition) {
            @Override
            protected ai.sagesource.opensagent.core.llm.tool.ToolResult doExecute(
                    ai.sagesource.opensagent.core.llm.tool.ToolCall toolCall) {
                return ai.sagesource.opensagent.core.llm.tool.ToolResult.success(toolCall.getId(), "ok");
            }
        };
    }
}
```

#### 文件 14: `open-sagent-tools/src/test/java/ai/sagesource/opensagent/tools/executor/ToolExecutorTest.java`

```java
package ai.sagesource.opensagent.tools.executor;

import ai.sagesource.opensagent.core.llm.tool.ToolCall;
import ai.sagesource.opensagent.core.llm.tool.ToolDefinition;
import ai.sagesource.opensagent.core.llm.tool.ToolResult;
import ai.sagesource.opensagent.tools.registry.ToolRegistry;
import ai.sagesource.opensagent.tools.tool.AbstractTool;
import ai.sagesource.opensagent.tools.tool.Tool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 工具执行器单元测试
 *
 * @author: sage.xue
 * @time: 2026/4/13
 */
class ToolExecutorTest {

    private ToolRegistry registry;
    private ToolExecutor executor;

    @BeforeEach
    void setUp() {
        registry = new ToolRegistry();
        executor = new ToolExecutor(registry);
    }

    @Test
    @DisplayName("执行单个ToolCall - 成功")
    void testExecuteSingle() {
        registry.register(createTestTool("echo", "回声工具"));

        ToolCall call = ToolCall.builder()
                .id("call_1")
                .name("echo")
                .arguments(Map.of("msg", "hello"))
                .build();

        ToolResult result = executor.execute(call);
        assertTrue(result.isSuccess());
        assertEquals("call_1", result.getToolCallId());
    }

    @Test
    @DisplayName("批量执行ToolCall - 成功")
    void testExecuteAll() {
        registry.register(createTestTool("echo", "回声工具"));

        List<ToolCall> calls = List.of(
                ToolCall.builder().id("call_1").name("echo").arguments(Map.of()).build(),
                ToolCall.builder().id("call_2").name("echo").arguments(Map.of()).build()
        );

        List<ToolResult> results = executor.executeAll(calls);
        assertEquals(2, results.size());
        assertTrue(results.get(0).isSuccess());
        assertTrue(results.get(1).isSuccess());
    }

    @Test
    @DisplayName("执行不存在的工具 - 失败")
    void testExecuteNotExist() {
        ToolCall call = ToolCall.builder()
                .id("call_1")
                .name("not_exist")
                .build();

        assertThrows(ai.sagesource.opensagent.core.llm.exception.OpenSagentToolException.class,
                () -> executor.execute(call));
    }

    private Tool createTestTool(String name, String description) {
        ToolDefinition definition = ToolDefinition.builder()
                .name(name)
                .description(description)
                .build();
        return new AbstractTool(definition) {
            @Override
            protected ToolResult doExecute(ToolCall toolCall) {
                return ToolResult.success(toolCall.getId(), "ok");
            }
        };
    }
}
```

## 3. 影响范围分析

### 3.1 模块依赖关系

```
open-sagent-core (抽象定义)
    ├── ToolDefinition / ToolCall / ToolResult / ToolParameterType
    ├── OpenSagentToolException (继承OpenSagentException)
    └── AssistantCompletionMessage (扩展 toolCalls 字段)

open-sagent-tools (执行框架)
    ├── 依赖 open-sagent-core
    ├── Tool / AbstractTool (接口与基类)
    ├── ToolRegistry (注册表)
    ├── ToolExecutor (执行器)
    └── ToolUtils (便捷方法)

open-sagent-infrastructure (后续适配)
    └── 需将 ToolDefinition 转换为 OpenAI SDK 的 ChatCompletionTool
```

### 3.2 影响范围

| 模块 | 影响说明 |
|------|----------|
| open-sagent-core | 新增Tool核心模型，扩展Assistant消息，无破坏性变更 |
| open-sagent-tools | 新增src目录及Tool执行框架，从空模块变为有代码 |
| open-sagent-infrastructure | 无直接影响，后续需实现ToolDefinition到OpenAI格式的转换 |
| open-sagent-web | 无直接影响 |
| open-sagent-cli | 无直接影响 |

### 3.3 扩展性说明

1. **新增参数类型**：通过 `ToolParameterType` 枚举扩展，不影响现有代码
2. **新增工具**：开发者只需实现 `Tool` 接口或继承 `AbstractTool`，注册到 `ToolRegistry` 即可
3. **厂商适配**：Infrastructure层负责将 `ToolDefinition` 转换为各厂商SDK的Tool格式
4. **异步执行**：`ToolExecutor` 后续可扩展为异步执行模式（如返回 `CompletableFuture<List<ToolResult>>`）

## 4. 测试计划

### 4.1 单元测试

| 测试类 | 测试内容 |
|--------|----------|
| `ToolDefinitionTest` | Tool定义构建、Schema生成、参数校验 |
| `ToolCallTest` | ToolCall创建、参数解析 |
| `ToolResultTest` | 成功/失败结果创建 |
| `ToolRegistryTest` | 注册、获取、重复注册、不存在异常 |
| `ToolExecutorTest` | 单条执行、批量执行、异常处理 |
| `ToolUtilsTest` | property构建、functions转换 |

### 4.2 集成测试

| 测试类 | 测试内容 |
|--------|----------|
| `ToolIntegrationTest` | 完整链路：定义 → 注册 → 执行 → 结果转换 |

## 5. 方案变更记录

| 变更 | 时间 | 说明 |
|------|------|------|
| 初始版本 | 2026-04-13 | 完成Tool模块方案设计 |

## 6. 评审记录

| 评审人 | 时间 | 结论 | 备注 |
|--------|------|------|------|
| User | 2026-04-13 | 通过 | 方案评审通过，可以实施 |
