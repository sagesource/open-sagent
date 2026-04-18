package ai.sagesource.opensagent.core.agent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AgentConfig单元测试
 *
 * @author: sage.xue
 * @time: 2026/4/16
 */
class AgentConfigTest {

    @Test
    @DisplayName("默认配置 - 工具开关关闭")
    void testDefaultConfig() {
        AgentConfig config = AgentConfig.builder().build();

        assertFalse(config.isEnableTools());
        assertNull(config.getMaxIterations());
        assertNull(config.getTemperature());
        assertNull(config.getMaxTokens());
    }

    @Test
    @DisplayName("自定义配置 - 成功")
    void testCustomConfig() {
        AgentConfig config = AgentConfig.builder()
                .enableTools(true)
                .maxIterations(5)
                .temperature(0.7)
                .maxTokens(2048)
                .build();

        assertTrue(config.isEnableTools());
        assertEquals(5, config.getMaxIterations());
        assertEquals(0.7, config.getTemperature());
        assertEquals(2048, config.getMaxTokens());
    }
}
