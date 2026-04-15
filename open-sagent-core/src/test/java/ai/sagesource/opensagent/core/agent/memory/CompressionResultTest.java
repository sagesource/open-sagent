package ai.sagesource.opensagent.core.agent.memory;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CompressionResult单元测试
 *
 * @author: sage.xue
 * @time: 2026/4/14
 */
class CompressionResultTest {

    @Test
    @DisplayName("创建成功压缩结果 - 成功")
    void testSuccessResult() {
        MemoryItem item = MemoryItem.builder()
                .memoryItemId("mem-001")
                .content("压缩内容")
                .build();

        CompressionResult result = CompressionResult.success(item);

        assertTrue(result.isSuccess());
        assertEquals(item, result.getMemoryItem());
        assertEquals("记忆压缩成功", result.getMessage());
    }

    @Test
    @DisplayName("创建跳过压缩结果 - 成功")
    void testSkippedResult() {
        CompressionResult result = CompressionResult.skipped("无可压缩内容");

        assertFalse(result.isSuccess());
        assertNull(result.getMemoryItem());
        assertEquals("无可压缩内容", result.getMessage());
    }
}
