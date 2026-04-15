package ai.sagesource.opensagent.infrastructure.agent.memory;

import ai.sagesource.opensagent.core.agent.memory.CompressionResult;
import ai.sagesource.opensagent.core.agent.memory.MemoryItem;
import ai.sagesource.opensagent.core.llm.message.AssistantCompletionMessage;
import ai.sagesource.opensagent.core.llm.message.CompletionMessage;
import ai.sagesource.opensagent.core.llm.message.UserCompletionMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SimpleMemory单元测试
 *
 * @author: sage.xue
 * @time: 2026/4/14
 */
class SimpleMemoryTest {

    @Test
    @DisplayName("添加单条消息 - 成功")
    void testAddMessage() {
        SimpleMemory memory = new SimpleMemory();
        CompletionMessage message = UserCompletionMessage.of("你好");

        memory.addMessage(message);

        assertEquals(1, memory.getMessages().size());
        assertEquals(1, memory.getUncompressedMessages().size());
    }

    @Test
    @DisplayName("批量添加消息 - 成功")
    void testAddMessages() {
        SimpleMemory memory = new SimpleMemory();
        List<CompletionMessage> messages = Arrays.asList(
                UserCompletionMessage.of("你好"),
                AssistantCompletionMessage.of("你好，有什么可以帮你的？")
        );

        memory.addMessages(messages);

        assertEquals(2, memory.getMessages().size());
        assertEquals(2, memory.getUncompressedMessages().size());
    }

    @Test
    @DisplayName("压缩消息 - 成功")
    void testCompress() {
        SimpleMemory memory = new SimpleMemory();
        memory.addMessage(UserCompletionMessage.of("今天天气怎么样？"));
        memory.addMessage(AssistantCompletionMessage.of("今天是晴天。"));

        CompressionResult result = memory.compress();

        assertTrue(result.isSuccess());
        assertNotNull(result.getMemoryItem());
        assertEquals(0, memory.getUncompressedMessages().size());
        assertEquals(1, memory.getMemoryItems().size());
    }

    @Test
    @DisplayName("无消息时压缩 - 返回跳过")
    void testCompressWhenEmpty() {
        SimpleMemory memory = new SimpleMemory();

        CompressionResult result = memory.compress();

        assertFalse(result.isSuccess());
        assertEquals("无可压缩的对话历史", result.getMessage());
    }

    @Test
    @DisplayName("清空记忆 - 成功")
    void testClear() {
        SimpleMemory memory = new SimpleMemory();
        memory.addMessage(UserCompletionMessage.of("你好"));
        memory.compress();

        memory.clear();

        assertTrue(memory.getMessages().isEmpty());
        assertTrue(memory.getUncompressedMessages().isEmpty());
        assertTrue(memory.getMemoryItems().isEmpty());
    }

    @Test
    @DisplayName("获取最后消息ID - 成功")
    void testGetLastMessageId() {
        SimpleMemory memory = new SimpleMemory();
        UserCompletionMessage message = UserCompletionMessage.builder()
                .messageId("msg-001")
                .contents(new java.util.ArrayList<>(List.of(
                        ai.sagesource.opensagent.core.llm.message.TextContent.builder().text("你好").build())))
                .build();

        memory.addMessage(message);

        assertEquals("msg-001", memory.getLastMessageId());
    }

    @Test
    @DisplayName("获取最后记忆项ID - 成功")
    void testGetLastMemoryItemId() {
        SimpleMemory memory = new SimpleMemory();
        memory.addMessage(UserCompletionMessage.of("你好"));
        CompressionResult result = memory.compress();

        assertNotNull(memory.getLastMemoryItemId());
        assertEquals(result.getMemoryItem().getMemoryItemId(), memory.getLastMemoryItemId());
    }

    @Test
    @DisplayName("压缩结果包含关联ID - 成功")
    void testCompressionResultContainsRelationIds() {
        SimpleMemory memory = new SimpleMemory();
        UserCompletionMessage msg1 = UserCompletionMessage.builder()
                .messageId("msg-001")
                .contents(new java.util.ArrayList<>(List.of(
                        ai.sagesource.opensagent.core.llm.message.TextContent.builder().text("你好").build())))
                .build();
        memory.addMessage(msg1);

        CompressionResult first = memory.compress();
        assertNotNull(first.getMemoryItem());
        assertEquals("msg-001", first.getMemoryItem().getLastMessageId());
        assertNull(first.getMemoryItem().getLastMemoryItemId());

        UserCompletionMessage msg2 = UserCompletionMessage.builder()
                .messageId("msg-002")
                .contents(new java.util.ArrayList<>(List.of(
                        ai.sagesource.opensagent.core.llm.message.TextContent.builder().text("再见").build())))
                .build();
        memory.addMessage(msg2);

        CompressionResult second = memory.compress();
        assertNotNull(second.getMemoryItem());
        assertEquals("msg-002", second.getMemoryItem().getLastMessageId());
        assertEquals(first.getMemoryItem().getMemoryItemId(), second.getMemoryItem().getLastMemoryItemId());
    }
}
