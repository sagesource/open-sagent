package ai.sagesource.opensagent.core.agent;

import ai.sagesource.opensagent.core.llm.message.AssistantCompletionMessage;
import ai.sagesource.opensagent.core.llm.tool.ToolCall;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AgentResponse单元测试
 *
 * @author: sage.xue
 * @time: 2026/4/16
 */
class AgentResponseTest {

    @Test
    @DisplayName("创建响应 - 成功")
    void testCreateResponse() {
        AssistantCompletionMessage message = AssistantCompletionMessage.of("你好");
        AgentResponse response = AgentResponse.builder()
                .responseId("resp-001")
                .message(message)
                .finishReason("stop")
                .build();

        assertEquals("resp-001", response.getResponseId());
        assertEquals("你好", response.getMessage().getTextContent());
        assertEquals("stop", response.getFinishReason());
        assertTrue(response.getToolResults().isEmpty());
    }

    @Test
    @DisplayName("响应包含工具调用 - 成功")
    void testHasToolCalls() {
        AssistantCompletionMessage message = AssistantCompletionMessage.builder()
                .contents(new ArrayList<>(List.of(
                        ai.sagesource.opensagent.core.llm.message.TextContent.builder().text("调用工具").build())))
                .toolCalls(new ArrayList<>(List.of(
                        ToolCall.builder().id("call-1").name("weather").build())))
                .build();

        AgentResponse response = AgentResponse.builder()
                .message(message)
                .build();

        assertTrue(response.hasToolCalls());
    }

    @Test
    @DisplayName("响应不包含工具调用 - 成功")
    void testNoToolCalls() {
        AssistantCompletionMessage message = AssistantCompletionMessage.of("纯文本回复");

        AgentResponse response = AgentResponse.builder()
                .message(message)
                .build();

        assertFalse(response.hasToolCalls());
    }
}
