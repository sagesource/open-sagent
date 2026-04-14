package ai.sagesource.opensagent.core.agent.prompt;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PromptRenderContext单元测试
 *
 * @author: sage.xue
 * @time: 2026/4/14
 */
class PromptRenderContextTest {

    @Test
    @DisplayName("基于Map创建上下文 - 成功")
    void testOfWithMap() {
        Map<String, String> variables = new HashMap<>();
        variables.put("name", "OpenSagent");

        PromptRenderContext context = PromptRenderContext.of(variables);

        assertNotNull(context);
        assertEquals("OpenSagent", context.getVariables().get("name"));
    }

    @Test
    @DisplayName("创建空上下文 - 成功")
    void testEmpty() {
        PromptRenderContext context = PromptRenderContext.empty();

        assertNotNull(context);
        assertNotNull(context.getVariables());
        assertTrue(context.getVariables().isEmpty());
    }

    @Test
    @DisplayName("基于null Map创建上下文 - 成功")
    void testOfWithNullMap() {
        PromptRenderContext context = PromptRenderContext.of(null);

        assertNotNull(context);
        assertNotNull(context.getVariables());
        assertTrue(context.getVariables().isEmpty());
    }
}
