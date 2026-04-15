package ai.sagesource.opensagent.infrastructure.agent.memory;

import ai.sagesource.opensagent.core.agent.memory.*;
import ai.sagesource.opensagent.core.llm.message.CompletionMessage;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 基于内存的简单Memory实现
 * <p>
 * 将某一个对话窗的对话历史保存在内存中；无记忆压缩功能（压缩操作直接返回跳过结果）
 *
 * @author: sage.xue
 * @time: 2026/4/14
 */
@Slf4j
public class SimpleMemory implements Memory {

    /**
     * 完整对话历史
     */
    private final List<CompletionMessage> messages = new ArrayList<>();

    /**
     * 未压缩对话历史
     */
    private final List<CompletionMessage> uncompressedMessages = new ArrayList<>();

    /**
     * 记忆历史（SimpleMemory中为空列表，仅作结构保留）
     */
    private final List<MemoryItem> memoryItems = new ArrayList<>();

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

            // SimpleMemory 无实际压缩能力，仅将所有未压缩对话的文本拼接作为记忆内容
            StringBuilder sb = new StringBuilder();
            String lastMsgId = null;
            for (CompletionMessage msg : uncompressedMessages) {
                sb.append("[").append(msg.getRole()).append("]: ").append(msg.getTextContent()).append("\n");
                if (msg.getMessageId() != null) {
                    lastMsgId = msg.getMessageId();
                }
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
            uncompressedMessages.clear();

            log.info("> Memory | 简单压缩完成, memoryItemId: {} <", item.getMemoryItemId());
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
