package ai.sagesource.opensagent.core.agent.memory;

import ai.sagesource.opensagent.core.llm.message.CompletionMessage;

import java.util.List;

/**
 * 记忆压缩策略接口
 * <p>
 * 定义记忆压缩的具体策略，不同实现可采用不同的压缩算法（如摘要、向量化、关键词提取等）
 *
 * @author: sage.xue
 * @time: 2026/4/14
 */
public interface MemoryCompressionStrategy {

    /**
     * 执行记忆压缩
     * <p>
     * 基于现有的记忆历史和未压缩的对话历史，生成新的记忆项内容
     *
     * @param memoryItems           已有的记忆历史列表
     * @param uncompressedMessages  未压缩的对话历史列表
     * @param lastMemoryItemId      最后一条记忆历史ID（可能为null）
     * @param lastMessageId         最后一条对话ID（可能为null）
     * @return 压缩后的记忆项内容
     */
    String compress(
            List<MemoryItem> memoryItems,
            List<CompletionMessage> uncompressedMessages,
            String lastMemoryItemId,
            String lastMessageId
    );
}
