package ai.sagesource.opensagent.core.agent.memory;

import ai.sagesource.opensagent.core.llm.message.CompletionMessage;

import java.util.List;

/**
 * Memory抽象接口
 * <p>
 * 定义Agent记忆管理的基本行为，包括对话历史的添加、查询和记忆压缩
 *
 * @author: sage.xue
 * @time: 2026/4/14
 */
public interface Memory {

    /**
     * 添加对话历史
     *
     * @param message 对话消息
     */
    void addMessage(CompletionMessage message);

    /**
     * 批量添加对话历史
     *
     * @param messages 对话消息列表
     */
    void addMessages(List<CompletionMessage> messages);

    /**
     * 获取所有对话历史（完整的历史对话信息列表）
     *
     * @return 对话消息列表
     */
    List<CompletionMessage> getMessages();

    /**
     * 获取未压缩的对话历史
     *
     * @return 未压缩的对话消息列表
     */
    List<CompletionMessage> getUncompressedMessages();

    /**
     * 获取所有压缩后的记忆历史
     *
     * @return 记忆项列表
     */
    List<MemoryItem> getMemoryItems();

    /**
     * 判断当前是否需要进行记忆压缩
     * <p>
     * 调用方可在执行 {@link #compress()} 之前调用此方法，
     * 独立判断当前记忆状态是否满足压缩条件。
     *
     * @return true 表示需要进行压缩，false 表示暂无需压缩
     */
    boolean shouldCompress();

    /**
     * 执行记忆压缩并保存
     * <p>
     * 使用最新的记忆历史 + 未压缩对话历史 进行记忆压缩，压缩完成后清空未压缩对话历史
     *
     * @return 压缩结果
     */
    CompressionResult compress();

    /**
     * 清空所有对话历史和记忆
     */
    void clear();

    /**
     * 获取最后一条对话消息的ID
     *
     * @return 消息ID，可能为null
     */
    String getLastMessageId();

    /**
     * 获取最后一条记忆项的ID
     *
     * @return 记忆项ID，可能为null
     */
    String getLastMemoryItemId();
}
