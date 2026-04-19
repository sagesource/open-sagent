package ai.sagesource.opensagent.infrastructure.agent.memory;

import ai.sagesource.opensagent.core.llm.message.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MessageJsonUtils 单元测试
 *
 * @author: sage.xue
 * @time: 2026/4/19
 */
class MessageJsonUtilsTest {

    @Test
    @DisplayName("序列化与反序列化 UserCompletionMessage - 成功")
    void testSerializeDeserializeUserMessage() {
        UserCompletionMessage msg = UserCompletionMessage.builder()
                .messageId("msg-001")
                .contents(new java.util.ArrayList<>(java.util.List.of(
                        TextContent.builder().text("你好").build())))
                .build();

        String json = MessageJsonUtils.toJson(msg);
        assertNotNull(json);
        assertTrue(json.contains("UserCompletionMessage"));

        CompletionMessage deserialized = MessageJsonUtils.fromJson(json);
        assertTrue(deserialized instanceof UserCompletionMessage);
        assertEquals("msg-001", deserialized.getMessageId());
        assertEquals("你好", deserialized.getTextContent());
        assertEquals(MessageRole.USER, deserialized.getRole());
    }

    @Test
    @DisplayName("序列化与反序列化 AssistantCompletionMessage - 成功")
    void testSerializeDeserializeAssistantMessage() {
        AssistantCompletionMessage msg = AssistantCompletionMessage.builder()
                .messageId("msg-002")
                .contents(new java.util.ArrayList<>(java.util.List.of(
                        TextContent.builder().text("你好，有什么可以帮你的？").build())))
                .build();

        String json = MessageJsonUtils.toJson(msg);
        CompletionMessage deserialized = MessageJsonUtils.fromJson(json);
        assertTrue(deserialized instanceof AssistantCompletionMessage);
        assertEquals("msg-002", deserialized.getMessageId());
        assertEquals("你好，有什么可以帮你的？", deserialized.getTextContent());
    }

    @Test
    @DisplayName("序列化与反序列化 ToolCompletionMessage - 成功")
    void testSerializeDeserializeToolMessage() {
        ToolCompletionMessage msg = ToolCompletionMessage.of("call-1", "weather_tool", "晴天");

        String json = MessageJsonUtils.toJson(msg);
        CompletionMessage deserialized = MessageJsonUtils.fromJson(json);
        assertTrue(deserialized instanceof ToolCompletionMessage);
        assertEquals("晴天", deserialized.getTextContent());
        assertEquals(MessageRole.TOOL, deserialized.getRole());
    }
}
