---
name: llm-tool-definition
description: 如何定义一个Tool（基于注解方式）
---

# Tool定义指南

基于`@Tool` + `@ToolParam`注解体系定义可调用工具，支持一个类中定义多个工具方法。

## 使用说明

1. 在任意Java类的方法上标注`@Tool`注解
2. 在方法参数上标注`@ToolParam`注解（**建议显式指定name**，避免编译后参数名丢失）
3. 使用`ToolMetadataParser.parse(Class<?>)`解析类上的注解
4. 将解析结果包装为`AnnotatedTool`并注册到`ToolRegistry`
5. 通过`ToolExecutor`执行工具调用

## 完整示例

### 步骤1：定义工具类

```java
package ai.sagesource.opensagent.example.tools;

import ai.sagesource.opensagent.core.llm.tool.ToolParameterType;
import ai.sagesource.opensagent.infrastructure.llm.tool.annotation.Tool;
import ai.sagesource.opensagent.infrastructure.llm.tool.annotation.ToolParam;

public class WeatherService {

    @Tool(name = "get_weather", description = "获取指定城市的天气信息")
    public String getWeather(
            @ToolParam(name = "city", description = "城市名称", type = ToolParameterType.STRING) String city,
            @ToolParam(name = "unit", description = "温度单位", type = ToolParameterType.STRING, required = false) String unit) {
        String u = unit != null ? unit : "celsius";
        return city + " 天气：晴天，25°" + ("fahrenheit".equals(u) ? "F" : "C");
    }
}
```

### 步骤2：解析并注册工具

```java
package ai.sagesource.opensagent.example;

import ai.sagesource.opensagent.core.llm.tool.Tool;
import ai.sagesource.opensagent.core.llm.tool.ToolExecutor;
import ai.sagesource.opensagent.core.llm.tool.ToolRegistry;
import ai.sagesource.opensagent.infrastructure.llm.tool.AnnotatedTool;
import ai.sagesource.opensagent.infrastructure.llm.tool.metadata.ToolMetadata;
import ai.sagesource.opensagent.infrastructure.llm.tool.parser.ToolMetadataParser;
import ai.sagesource.opensagent.example.tools.WeatherService;

import java.util.List;

public class ToolRegistrationExample {

    public static void main(String[] args) {
        // 1. 创建目标实例
        WeatherService weatherService = new WeatherService();

        // 2. 解析类上的所有@Tool方法
        List<ToolMetadata> metadatas = ToolMetadataParser.parse(WeatherService.class);

        // 3. 创建注册表
        ToolRegistry registry = new ToolRegistry();
        for (ToolMetadata metadata : metadatas) {
            Tool tool = new AnnotatedTool(weatherService, metadata);
            registry.register(tool);
        }

        // 4. 创建执行器
        ToolExecutor executor = new ToolExecutor(registry);

        // 5. 执行工具调用...
    }
}
```

### 步骤3：执行工具调用

```java
import ai.sagesource.opensagent.core.llm.tool.ToolCall;
import ai.sagesource.opensagent.core.llm.tool.ToolResult;

import java.util.Map;

// 构造ToolCall（通常由LLM返回）
ToolCall call = ToolCall.builder()
        .id("call_123")
        .name("get_weather")
        .arguments(Map.of("city", "北京", "unit", "celsius"))
        .build();

// 执行并获取结果
ToolResult result = executor.execute(call);
if (result.isSuccess()) {
    System.out.println(result.getContent()); // 输出：北京 天气：晴天，25°C
} else {
    System.err.println(result.getErrorMessage());
}
```

## 关键注解说明

### @Tool

| 属性 | 必填 | 说明 |
|------|------|------|
| name | 是 | 工具名称，全局唯一 |
| description | 否 | 工具描述，帮助LLM理解用途 |

### @ToolParam

| 属性 | 必填 | 默认值 | 说明 |
|------|------|--------|------|
| name | 否 | 参数名 | **建议显式指定**，避免编译后参数名丢失变为`arg0` |
| description | 否 | "" | 参数描述 |
| type | 否 | STRING | 参数类型：`STRING`/`INTEGER`/`NUMBER`/`BOOLEAN`/`ARRAY`/`OBJECT` |
| required | 否 | true | 是否必填 |
| enumValues | 否 | {} | 枚举值列表 |

## 类型自动映射规则

当未显式指定`@ToolParam.type`时，`ToolMetadataParser`会根据Java参数类型自动映射：

| Java类型 | 映射为 |
|----------|--------|
| `String` | `STRING` |
| `int`/`Integer`/`long`/`Long`/`short`/`Short`/`byte`/`Byte` | `INTEGER` |
| `double`/`Double`/`float`/`Float` | `NUMBER` |
| `boolean`/`Boolean` | `BOOLEAN` |
| 数组或`List` | `ARRAY` |
| 其他对象 | `OBJECT` |

## 混合注册说明

`ToolRegistry`基于`Tool`接口，可以同时注册`AnnotatedTool`和自定义的`Tool`实现：

```java
// 注册注解工具
registry.register(new AnnotatedTool(service, metadata));

// 注册自定义Tool实现
registry.register(new CustomTool());
```

## 注意事项

1. **参数名必须显式指定**：如果不带`-parameters`编译参数，Java反射获取的参数名会是`arg0`、`arg1`，此时必须显式指定`@ToolParam(name = "xxx")`
2. **工具名称全局唯一**：同一个`ToolRegistry`中不允许注册同名工具
3. **异常处理**：工具执行过程中的业务异常会被`AnnotatedTool`捕获，包装为`ToolResult.failure()`返回
