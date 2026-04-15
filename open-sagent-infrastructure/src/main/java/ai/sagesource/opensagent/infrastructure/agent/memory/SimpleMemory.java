package ai.sagesource.opensagent.infrastructure.agent.memory;

import ai.sagesource.opensagent.core.agent.memory.*;
import ai.sagesource.opensagent.core.llm.message.CompletionMessage;
import ai.sagesource.opensagent.core.llm.message.MessageRole;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 基于内存的简单Memory实现
 * <p>
 * 将某一个对话窗的对话历史保存在内存中；
 * 使用滑动时间窗模式进行记忆压缩，压缩时保留最近窗口大小的会话信息；
 * 压缩时剔除SYSTEM消息，多个TOOL消息只保留最新一次
 *
 * @author: sage.xue
 * @time: 2026/4/14
 */
@Slf4j
public class SimpleMemory implements Memory {

    /**
     * 默认滑动窗口大小
     */
    private static final int DEFAULT_WINDOW_SIZE = 50;

    /**
     * 滑动窗口大小（每次压缩时保留的最近未压缩消息数量）
     */
    private final int windowSize;

    /**
     * 完整对话历史
     */
    private final List<CompletionMessage> messages = new ArrayList<>();

    /**
     * 未压缩对话历史
     */
    private final List<CompletionMessage> uncompressedMessages = new ArrayList<>();

    /**
     * 记忆历史
     */
    private final List<MemoryItem> memoryItems = new ArrayList<>();

    public SimpleMemory() {
        this(DEFAULT_WINDOW_SIZE);
    }

    public SimpleMemory(int windowSize) {
        this.windowSize = windowSize >= 0 ? windowSize : DEFAULT_WINDOW_SIZE;
    }

    @Override
    public void addMessage(CompletionMessage message) {
        if (message == null) {
            return;
        }
        synchronized (this) {
            messages.add(message);
            uncompressedMessages.add(message);
        }
        log.info("> Memory | 添加消息成功, role: {} <", message.getRole());
    }

    @Override
    public void addMessages(List<CompletionMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return;
        }
        for (CompletionMessage message : messages) {
            addMessage(message);
        }
    }

    @Override
    public List<CompletionMessage> getMessages() {
        synchronized (this) {
            return new ArrayList<>(messages);
        }
    }

    @Override
    public List<CompletionMessage> getUncompressedMessages() {
        synchronized (this) {
            return new ArrayList<>(uncompressedMessages);
        }
    }

    @Override
    public List<MemoryItem> getMemoryItems() {
        synchronized (this) {
            return new ArrayList<>(memoryItems);
        }
    }

    @Override
    public CompressionResult compress() {
        synchronized (this) {
            if (uncompressedMessages.isEmpty()) {
                log.warn("> Memory | 无可压缩的对话历史 <");
                return CompressionResult.skipped("无可压缩的对话历史");
            }

            int compressCount = Math.max(0, uncompressedMessages.size() - windowSize);
            if (compressCount == 0) {
                log.warn("> Memory | 未压缩消息数量未超过滑动窗口阈值 {}，无需压缩 <", windowSize);
                return CompressionResult.skipped("未压缩消息数量未超过滑动窗口阈值，无需压缩");
            }

            List<CompletionMessage> toCompress = new ArrayList<>(uncompressedMessages.subList(0, compressCount));

            // 找出最后一个 TOOL 消息的索引
            int lastToolIndex = -1;
            for (int i = 0; i < toCompress.size(); i++) {
                if (toCompress.get(i).getRole() == MessageRole.TOOL) {
                    lastToolIndex = i;
                }
            }

            StringBuilder sb = new StringBuilder();
            String lastMsgId = null;
            for (int i = 0; i < toCompress.size(); i++) {
                CompletionMessage msg = toCompress.get(i);
                if (msg.getRole() == MessageRole.SYSTEM) {
                    continue;
                }
                if (msg.getRole() == MessageRole.TOOL && i != lastToolIndex) {
                    continue;
                }
                sb.append("[").append(msg.getRole()).append("]: ").append(msg.getTextContent()).append("\n");
                if (msg.getMessageId() != null) {
                    lastMsgId = msg.getMessageId();
                }
            }

            // 清除已压缩部分
            for (int i = 0; i < compressCount; i++) {
                uncompressedMessages.remove(0);
            }

            // 如果过滤后没有有效内容，不生成 MemoryItem
            if (sb.length() == 0) {
                log.info("> Memory | 过滤后无可压缩的有效对话历史 <");
                return CompressionResult.skipped("过滤后无可压缩的有效对话历史");
            }

            String lastMemoryItemId = memoryItems.isEmpty() ? null : memoryItems.get(memoryItems.size() - 1).getMemoryItemId();

            MemoryItem item = MemoryItem.builder()
                    .memoryItemId(UUID.randomUUID().toString())
                    .content(sb.toString().trim())
                    .lastMessageId(lastMsgId)
                    .lastMemoryItemId(lastMemoryItemId)
                    .timestamp(System.currentTimeMillis())
                    .build();

            memoryItems.add(item);

            log.info("> Memory | 简单压缩完成, memoryItemId: {}, 压缩消息数: {}, 保留消息数: {} <",
                    item.getMemoryItemId(), compressCount, uncompressedMessages.size());
            return CompressionResult.success(item);
        }
    }

    @Override
    public void clear() {
        synchronized (this) {
            messages.clear();
            uncompressedMessages.clear();
            memoryItems.clear();
        }
        log.info("> Memory | 清空所有记忆 <");
    }

    @Override
    public String getLastMessageId() {
        synchronized (this) {
            if (messages.isEmpty()) {
                return null;
            }
            CompletionMessage last = messages.get(messages.size() - 1);
            return last != null ? last.getMessageId() : null;
        }
    }

    @Override
    public String getLastMemoryItemId() {
        synchronized (this) {
            if (memoryItems.isEmpty()) {
                return null;
            }
            return memoryItems.get(memoryItems.size() - 1).getMemoryItemId();
        }
    }
}
