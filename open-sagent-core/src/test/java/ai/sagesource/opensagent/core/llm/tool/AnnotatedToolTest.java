package ai.sagesource.opensagent.core.llm.tool;

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
