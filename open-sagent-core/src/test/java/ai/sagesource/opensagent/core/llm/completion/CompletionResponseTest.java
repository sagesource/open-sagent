package ai.sagesource.opensagent.core.llm.completion;

import ai.sagesource.opensagent.core.llm.message.AssistantCompletionMessage;
import ai.sagesource.opensagent.core.llm.tool.ToolCall;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CompletionResponse单元测试
 *
 * @author: sage.xue
 * @time: 2026/4/14
 */
class CompletionResponseTest {

    @Test
    @DisplayName("构建CompletionResponse - 成功")
    void testBuildResponse() {
        CompletionResponse response = CompletionResponse.builder()
                .responseId("resp_123")
                .model("gpt-4")
                .finishReason("stop")
                .usage(TokenUsage.builder().promptTokens(10).completionTokens(5).totalTokens(15).build())
                .message(AssistantCompletionMessage.of("Hello"))
                .build();

        assertEquals("resp_123", response.getResponseId());
        assertEquals("gpt-4", response.getModel());
        assertEquals("stop", response.getFinishReason());
        assertNotNull(response.getUsage());
        assertFalse(response.hasToolCalls());
    }

    @Test
    @DisplayName("包含ToolCalls判断 - 成功")
    void testHasToolCalls() {
        List<ToolCall> toolCalls = new ArrayList<>();
        toolCalls.add(ToolCall.builder().id("call_1").name("add").arguments(Map.of("a", 1)).build());

        AssistantCompletionMessage msg = AssistantCompletionMessage.builder()
                .contents(new ArrayList<>(List.of(
                        ai.sagesource.opensagent.core.llm.message.TextContent.builder().text("").build())))
                .toolCalls(toolCalls)
                .build();

        CompletionResponse response = CompletionResponse.builder()
                .message(msg)
                .build();

        assertTrue(response.hasToolCalls());
    }
}
