package ai.sagesource.opensagent.core.agent.memory;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MemoryItem单元测试
 *
 * @author: sage.xue
 * @time: 2026/4/14
 */
class MemoryItemTest {

    @Test
    @DisplayName("创建记忆项 - 成功")
    void testCreateMemoryItem() {
        MemoryItem item = MemoryItem.builder()
                .memoryItemId("mem-001")
                .content("用户询问天气，助手回答晴天")
                .lastMessageId("msg-003")
                .lastMemoryItemId("mem-000")
                .timestamp(System.currentTimeMillis())
                .build();

        assertNotNull(item);
        assertEquals("mem-001", item.getMemoryItemId());
        assertEquals("用户询问天气，助手回答晴天", item.getContent());
        assertEquals("msg-003", item.getLastMessageId());
        assertEquals("mem-000", item.getLastMemoryItemId());
        assertNotNull(item.getTimestamp());
    }

    @Test
    @DisplayName("记忆项无关联ID - 成功")
    void testMemoryItemWithoutRelations() {
        MemoryItem item = MemoryItem.builder()
                .memoryItemId("mem-002")
                .content("首次压缩的记忆")
                .build();

        assertNotNull(item);
        assertNull(item.getLastMessageId());
        assertNull(item.getLastMemoryItemId());
    }
}
