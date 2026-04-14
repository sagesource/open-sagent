package ai.sagesource.opensagent.core.llm.completion;

import ai.sagesource.opensagent.core.llm.message.UserCompletionMessage;
import ai.sagesource.opensagent.core.llm.tool.ToolDefinition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CompletionRequest单元测试
 *
 * @author: sage.xue
 * @time: 2026/4/14
 */
class CompletionRequestTest {

    @Test
    @DisplayName("构建CompletionRequest默认值 - 成功")
    void testDefaultBuilder() {
        CompletionRequest request = CompletionRequest.builder().build();
        assertNotNull(request.getMessages());
        assertTrue(request.getMessages().isEmpty());
        assertNotNull(request.getTools());
        assertTrue(request.getTools().isEmpty());
        assertFalse(request.isStream());
    }

    @Test
    @DisplayName("添加消息和工具 - 成功")
    void testAddMessageAndTool() {
        CompletionRequest request = CompletionRequest.builder().build();
        request.addMessage(UserCompletionMessage.of("你好"));
        request.addTool(ToolDefinition.builder()
                .name("get_weather")
                .description("获取天气")
                .parameters(Map.of("city", Map.of("type", "string")))
                .required(List.of("city"))
                .build());

        assertEquals(1, request.getMessages().size());
        assertEquals(1, request.getTools().size());
    }
}
