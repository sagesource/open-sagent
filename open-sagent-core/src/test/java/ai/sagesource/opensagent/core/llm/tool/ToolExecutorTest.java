package ai.sagesource.opensagent.core.llm.tool;

import ai.sagesource.opensagent.core.llm.exception.OpenSagentToolException;
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

        assertThrows(OpenSagentToolException.class, () -> executor.execute(call));
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
