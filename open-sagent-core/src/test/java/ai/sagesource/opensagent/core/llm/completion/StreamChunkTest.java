package ai.sagesource.opensagent.core.llm.completion;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * StreamChunk单元测试
 *
 * @author: sage.xue
 * @time: 2026/4/14
 */
class StreamChunkTest {

    @Test
    @DisplayName("构建StreamChunk - 成功")
    void testBuildChunk() {
        StreamChunk chunk = StreamChunk.builder()
                .deltaText("Hello")
                .aggregatedText("Hello")
                .build();

        assertEquals("Hello", chunk.getDeltaText());
        assertTrue(chunk.hasDelta());
        assertFalse(chunk.isFinished());
    }

    @Test
    @DisplayName("空增量判断 - 成功")
    void testEmptyDelta() {
        StreamChunk chunk = StreamChunk.builder().build();
        assertFalse(chunk.hasDelta());
    }

    @Test
    @DisplayName("结束chunk判断 - 成功")
    void testFinishedChunk() {
        StreamChunk chunk = StreamChunk.builder()
                .finished(true)
                .finishReason("stop")
                .build();

        assertTrue(chunk.isFinished());
        assertEquals("stop", chunk.getFinishReason());
    }
}
