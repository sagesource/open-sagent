package ai.sagesource.opensagent.core.llm.tool;

import ai.sagesource.opensagent.core.llm.exception.OpenSagentToolException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
            protected ToolResult doExecute(ToolCall toolCall) {
                return ToolResult.success(toolCall.getId(), "ok");
            }
        };
    }
}
