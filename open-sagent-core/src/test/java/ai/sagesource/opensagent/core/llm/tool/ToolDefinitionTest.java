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
