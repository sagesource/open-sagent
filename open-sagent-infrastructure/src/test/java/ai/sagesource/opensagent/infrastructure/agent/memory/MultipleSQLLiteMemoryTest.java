package ai.sagesource.opensagent.infrastructure.agent.memory;

import ai.sagesource.opensagent.core.agent.memory.CompressionResult;
import ai.sagesource.opensagent.core.agent.memory.MemoryItem;
import ai.sagesource.opensagent.core.llm.completion.CompletionResponse;
import ai.sagesource.opensagent.core.llm.message.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MultipleSQLLiteMemory 单元测试
 * <p>
 * 每个测试使用独立的 sessionId 和临时数据库文件，确保测试隔离。
 *
 * @author: sage.xue
 * @time: 2026/4/19
 */
class MultipleSQLLiteMemoryTest {

    private static final String TEST_DB = "test-memory.db";

    @BeforeEach
    @AfterEach
    void cleanUp() {
        File dbFile = new File(TEST_DB);
        if (dbFile.exists()) {
            dbFile.delete();
        }
    }

    private MultipleSQLLiteMemory createMemory() {
        return new MultipleSQLLiteMemory(UUID.randomUUID().toString(), TEST_DB, 3, null, null);
    }

    @Test
    @DisplayName("添加单条消息并查询 - 成功")
    void testAddAndGetMessage() {
        MultipleSQLLiteMemory memory = createMemory();
        memory.addMessage(UserCompletionMessage.of("你好"));

        assertEquals(1, memory.getMessages().size());
        assertEquals(1, memory.getUncompressedMessages().size());
        assertEquals("你好", memory.getMessages().get(0).getTextContent());
    }

    @Test
    @DisplayName("批量添加消息 - 成功")
    void testAddMessages() {
        MultipleSQLLiteMemory memory = createMemory();
        List<CompletionMessage> messages = Arrays.asList(
                UserCompletionMessage.of("你好"),
                AssistantCompletionMessage.of("你好，有什么可以帮你的？")
        );

        memory.addMessages(messages);

        assertEquals(2, memory.getMessages().size());
        assertEquals(2, memory.getUncompressedMessages().size());
    }

    @Test
    @DisplayName("滑动窗口压缩 - 保留窗口内消息")
    void testCompressWithSlidingWindow() {
        MultipleSQLLiteMemory memory = createMemory();
        memory.addMessage(UserCompletionMessage.of("msg1"));
        memory.addMessage(UserCompletionMessage.of("msg2"));
        memory.addMessage(UserCompletionMessage.of("msg3"));
        memory.addMessage(UserCompletionMessage.of("msg4"));
        memory.addMessage(UserCompletionMessage.of("msg5"));

        CompressionResult result = memory.compress();

        assertTrue(result.isSuccess());
        assertEquals(3, memory.getUncompressedMessages().size());
        assertEquals(5, memory.getMessages().size());
        assertEquals(1, memory.getMemoryItems().size());
    }

    @Test
    @DisplayName("压缩时未超过窗口阈值 - 返回跳过")
    void testCompressWhenBelowThreshold() {
        MultipleSQLLiteMemory memory = new MultipleSQLLiteMemory(
                UUID.randomUUID().toString(), TEST_DB, 10, null, null);
        memory.addMessage(UserCompletionMessage.of("msg1"));
        memory.addMessage(UserCompletionMessage.of("msg2"));

        CompressionResult result = memory.compress();

        assertFalse(result.isSuccess());
        assertEquals("未压缩消息数量未超过滑动窗口阈值，无需压缩", result.getMessage());
    }

    @Test
    @DisplayName("压缩时剔除 SYSTEM 消息 - 成功")
    void testCompressFiltersSystemMessages() {
        MultipleSQLLiteMemory memory = new MultipleSQLLiteMemory(
                UUID.randomUUID().toString(), TEST_DB, 1, null, null);
        memory.addMessage(SystemCompletionMessage.of("系统提示"));
        memory.addMessage(UserCompletionMessage.of("msg1"));
        memory.addMessage(UserCompletionMessage.of("msg2"));

        CompressionResult result = memory.compress();

        assertTrue(result.isSuccess());
        assertNotNull(result.getMemoryItem());
        assertFalse(result.getMemoryItem().getContent().contains("SYSTEM"));
        assertTrue(result.getMemoryItem().getContent().contains("USER"));
    }

    @Test
    @DisplayName("压缩时 TOOL 消息只保留最新一次 - 成功")
    void testCompressKeepsLatestToolMessageOnly() {
        MultipleSQLLiteMemory memory = new MultipleSQLLiteMemory(
                UUID.randomUUID().toString(), TEST_DB, 1, null, null);
        memory.addMessage(ToolCompletionMessage.of("call-1", "tool1", "结果1"));
        memory.addMessage(UserCompletionMessage.of("谢谢"));
        memory.addMessage(ToolCompletionMessage.of("call-2", "tool2", "结果2"));
        memory.addMessage(UserCompletionMessage.of("后续消息"));

        CompressionResult result = memory.compress();

        assertTrue(result.isSuccess());
        String content = result.getMemoryItem().getContent();
        assertEquals(1, countOccurrences(content, "TOOL"));
        assertTrue(content.contains("结果2"));
        assertFalse(content.contains("结果1"));
    }

    @Test
    @DisplayName("清空记忆 - 成功")
    void testClear() {
        MultipleSQLLiteMemory memory = createMemory();
        memory.addMessage(UserCompletionMessage.of("你好"));
        memory.compress();

        memory.clear();

        assertTrue(memory.getMessages().isEmpty());
        assertTrue(memory.getUncompressedMessages().isEmpty());
        assertTrue(memory.getMemoryItems().isEmpty());
    }

    @Test
    @DisplayName("多会话隔离 - 成功")
    void testSessionIsolation() {
        String session1 = "session-1";
        String session2 = "session-2";

        MultipleSQLLiteMemory memory1 = new MultipleSQLLiteMemory(session1, TEST_DB, 3, null, null);
        MultipleSQLLiteMemory memory2 = new MultipleSQLLiteMemory(session2, TEST_DB, 3, null, null);

        memory1.addMessage(UserCompletionMessage.of("会话1的消息"));
        memory2.addMessage(UserCompletionMessage.of("会话2的消息"));

        assertEquals(1, memory1.getMessages().size());
        assertEquals(1, memory2.getMessages().size());
        assertEquals("会话1的消息", memory1.getMessages().get(0).getTextContent());
        assertEquals("会话2的消息", memory2.getMessages().get(0).getTextContent());
    }

    @Test
    @DisplayName("持久化恢复 - 成功")
    void testPersistenceRecovery() {
        String sessionId = UUID.randomUUID().toString();

        // 第一个实例写入数据
        MultipleSQLLiteMemory memory1 = new MultipleSQLLiteMemory(sessionId, TEST_DB, 2, null, null);
        memory1.addMessage(UserCompletionMessage.of("msg1"));
        memory1.addMessage(UserCompletionMessage.of("msg2"));
        memory1.addMessage(UserCompletionMessage.of("msg3"));
        memory1.compress();

        // 第二个实例读取数据
        MultipleSQLLiteMemory memory2 = new MultipleSQLLiteMemory(sessionId, TEST_DB, 2, null, null);

        assertEquals(3, memory2.getMessages().size());
        assertEquals(2, memory2.getUncompressedMessages().size());
        assertEquals(1, memory2.getMemoryItems().size());
    }

    @Test
    @DisplayName("LLM 智能压缩 - 成功")
    void testLLMCompression() {
        // Mock LLMCompletion
        ai.sagesource.opensagent.core.llm.completion.LLMCompletion mockCompletion = new ai.sagesource.opensagent.core.llm.completion.LLMCompletion() {
            @Override
            public CompletionResponse complete(ai.sagesource.opensagent.core.llm.completion.CompletionRequest request) {
                return CompletionResponse.builder()
                        .message(AssistantCompletionMessage.of("这是 LLM 压缩后的记忆摘要。"))
                        .build();
            }

            @Override
            public java.util.concurrent.CompletableFuture<CompletionResponse> completeAsync(ai.sagesource.opensagent.core.llm.completion.CompletionRequest request, java.util.concurrent.Executor executor) {
                return null;
            }

            @Override
            public ai.sagesource.opensagent.core.llm.completion.CompletionCancelToken stream(ai.sagesource.opensagent.core.llm.completion.CompletionRequest request, java.util.function.Consumer<ai.sagesource.opensagent.core.llm.completion.StreamChunk> consumer) {
                return null;
            }

            @Override
            public ai.sagesource.opensagent.core.llm.completion.CompletionCancelToken streamAsync(ai.sagesource.opensagent.core.llm.completion.CompletionRequest request, java.util.function.Consumer<ai.sagesource.opensagent.core.llm.completion.StreamChunk> consumer, java.util.concurrent.Executor executor) {
                return null;
            }
        };

        // Mock MemoryCompressionStrategy
        ai.sagesource.opensagent.core.agent.memory.MemoryCompressionStrategy mockStrategy =
                (memoryItems, uncompressedMessages, lastMemoryItemId, lastMessageId) -> "这是 LLM 压缩后的记忆摘要。";

        MultipleSQLLiteMemory memory = new MultipleSQLLiteMemory(
                UUID.randomUUID().toString(), TEST_DB, 1, mockCompletion, mockStrategy);
        memory.addMessage(UserCompletionMessage.of("请帮我总结这段对话。"));
        memory.addMessage(AssistantCompletionMessage.of("好的，这段对话的核心是..."));

        CompressionResult result = memory.compress();

        assertTrue(result.isSuccess());
        assertEquals("这是 LLM 压缩后的记忆摘要。", result.getMemoryItem().getContent());
    }

    @Test
    @DisplayName("获取最后消息 ID - 成功")
    void testGetLastMessageId() {
        MultipleSQLLiteMemory memory = createMemory();
        UserCompletionMessage msg = UserCompletionMessage.builder()
                .messageId("msg-last")
                .contents(new java.util.ArrayList<>(java.util.List.of(
                        TextContent.builder().text("你好").build())))
                .build();
        memory.addMessage(msg);

        assertEquals("msg-last", memory.getLastMessageId());
    }

    @Test
    @DisplayName("获取最后记忆项 ID - 成功")
    void testGetLastMemoryItemId() {
        MultipleSQLLiteMemory memory = new MultipleSQLLiteMemory(
                UUID.randomUUID().toString(), TEST_DB, 1, null, null);
        memory.addMessage(UserCompletionMessage.of("msg1"));
        memory.addMessage(UserCompletionMessage.of("msg2"));
        CompressionResult result = memory.compress();

        assertNotNull(memory.getLastMemoryItemId());
        assertEquals(result.getMemoryItem().getMemoryItemId(), memory.getLastMemoryItemId());
    }

    @Test
    @DisplayName("压缩结果包含关联 ID - 成功")
    void testCompressionResultContainsRelationIds() {
        MultipleSQLLiteMemory memory = new MultipleSQLLiteMemory(
                UUID.randomUUID().toString(), TEST_DB, 1, null, null);
        UserCompletionMessage msg1 = UserCompletionMessage.builder()
                .messageId("msg-001")
                .contents(new java.util.ArrayList<>(java.util.List.of(
                        TextContent.builder().text("你好").build())))
                .build();
        memory.addMessage(msg1);
        memory.addMessage(UserCompletionMessage.of("msg1-2"));
        CompressionResult first = memory.compress();
        assertNotNull(first.getMemoryItem());
        assertEquals("msg-001", first.getMemoryItem().getLastMessageId());
        assertNull(first.getMemoryItem().getLastMemoryItemId());

        UserCompletionMessage msg2 = UserCompletionMessage.builder()
                .messageId("msg-002")
                .contents(new java.util.ArrayList<>(java.util.List.of(
                        TextContent.builder().text("再见").build())))
                .build();
        memory.addMessage(msg2);
        memory.addMessage(UserCompletionMessage.of("msg2-2"));
        CompressionResult second = memory.compress();
        assertNotNull(second.getMemoryItem());
        assertEquals("msg-002", second.getMemoryItem().getLastMessageId());
        assertEquals(first.getMemoryItem().getMemoryItemId(), second.getMemoryItem().getLastMemoryItemId());
    }

    // ===== shouldCompress 相关测试 =====

    @Test
    @DisplayName("shouldCompress-空记忆返回false")
    void testShouldCompressWhenEmpty() {
        MultipleSQLLiteMemory memory = createMemory();
        assertFalse(memory.shouldCompress());
    }

    @Test
    @DisplayName("shouldCompress-未超过窗口阈值返回false")
    void testShouldCompressWhenBelowThreshold() {
        MultipleSQLLiteMemory memory = new MultipleSQLLiteMemory(
                UUID.randomUUID().toString(), TEST_DB, 10, null, null);
        memory.addMessage(UserCompletionMessage.of("msg1"));
        memory.addMessage(UserCompletionMessage.of("msg2"));

        assertFalse(memory.shouldCompress());
    }

    @Test
    @DisplayName("shouldCompress-超过窗口阈值返回true")
    void testShouldCompressWhenAboveThreshold() {
        MultipleSQLLiteMemory memory = createMemory();
        memory.addMessage(UserCompletionMessage.of("msg1"));
        memory.addMessage(UserCompletionMessage.of("msg2"));
        memory.addMessage(UserCompletionMessage.of("msg3"));
        memory.addMessage(UserCompletionMessage.of("msg4"));
        memory.addMessage(UserCompletionMessage.of("msg5"));

        assertTrue(memory.shouldCompress());
    }

    @Test
    @DisplayName("shouldCompress-压缩后再次判断返回false")
    void testShouldCompressAfterCompress() {
        MultipleSQLLiteMemory memory = createMemory();
        memory.addMessage(UserCompletionMessage.of("msg1"));
        memory.addMessage(UserCompletionMessage.of("msg2"));
        memory.addMessage(UserCompletionMessage.of("msg3"));
        memory.addMessage(UserCompletionMessage.of("msg4"));
        memory.addMessage(UserCompletionMessage.of("msg5"));

        assertTrue(memory.shouldCompress());
        memory.compress();
        assertFalse(memory.shouldCompress());
    }

    private int countOccurrences(String text, String sub) {
        int count = 0;
        int idx = 0;
        while ((idx = text.indexOf(sub, idx)) != -1) {
            count++;
            idx += sub.length();
        }
        return count;
    }
}
