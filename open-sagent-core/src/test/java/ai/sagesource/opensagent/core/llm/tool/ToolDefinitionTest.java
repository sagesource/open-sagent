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
