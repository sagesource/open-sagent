package ai.sagesource.opensagent.core.llm.message;

import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 开发者消息实现
 * <p>
 * 用于向模型提供指令（OpenAI o1系列模型等支持）
 *
 * @author: sage.xue
 * @time: 2026/4/13
 */
@Data
@Builder
public class DeveloperCompletionMessage implements CompletionMessage {

    /**
     * 消息ID
     */
    private String messageId;

    /**
     * 消息内容列表
     */
    @Builder.Default
    private List<MessageContent> contents = new ArrayList<>();

    @Override
    public MessageRole getRole() {
        return MessageRole.DEVELOPER;
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
     * 创建纯文本开发者消息（便捷方法）
     *
     * @param text 开发者指令文本
     * @return DeveloperCompletionMessage实例
     */
    public static DeveloperCompletionMessage of(String text) {
        return DeveloperCompletionMessage.builder()
                .contents(new ArrayList<>(List.of(TextContent.builder().text(text).build())))
                .build();
    }
}
