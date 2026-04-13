package ai.sagesource.opensagent.core.llm.message;

import ai.sagesource.opensagent.core.llm.tool.ToolCall;
import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 助手消息实现
 * <p>
 * 表示AI助手（大模型）的回复消息
 *
 * @author: sage.xue
 * @time: 2026/4/13
 */
@Data
@Builder
public class AssistantCompletionMessage implements CompletionMessage {

    /**
     * 消息ID
     */
    private String messageId;

    /**
     * 消息内容列表
     */
    @Builder.Default
    private List<MessageContent> contents = new ArrayList<>();

    /**
     * 是否完成思考（适用于有推理过程的模型）
     */
    @Builder.Default
    private boolean reasoningComplete = true;

    /**
     * 推理内容（可选）
     */
    private String reasoningContent;

    /**
     * 工具调用列表（当模型返回function calling时）
     */
    @Builder.Default
    private List<ToolCall> toolCalls = new ArrayList<>();

    @Override
    public MessageRole getRole() {
        return MessageRole.ASSISTANT;
    }

    @Override
    public List<MessageContent> getContents() {
        return contents;
    }

    @Override
    public String getMessageId() {
        return messageId;
    }

    @Override
    public void addContent(MessageContent content) {
        if (this.contents == null) {
            this.contents = new ArrayList<>();
        }
        this.contents.add(content);
    }

    /**
     * 创建纯文本助手消息（便捷方法）
     *
     * @param text 回复文本
     * @return AssistantCompletionMessage实例
     */
    public static AssistantCompletionMessage of(String text) {
        return AssistantCompletionMessage.builder()
                .contents(new ArrayList<>(List.of(TextContent.builder().text(text).build())))
                .build();
    }
}
