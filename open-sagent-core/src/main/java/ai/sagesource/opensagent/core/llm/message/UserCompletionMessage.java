package ai.sagesource.opensagent.core.llm.message;

import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 用户消息实现
 * <p>
 * 表示用户发送的消息，支持文本、图片、文件等多种内容
 *
 * @author: sage.xue
 * @time: 2026/4/13
 */
@Data
@Builder
public class UserCompletionMessage implements CompletionMessage {

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
        return MessageRole.USER;
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
     * 创建纯文本用户消息（便捷方法）
     *
     * @param text 文本内容
     * @return UserCompletionMessage实例
     */
    public static UserCompletionMessage of(String text) {
        return UserCompletionMessage.builder()
                .contents(new ArrayList<>(List.of(TextContent.builder().text(text).build())))
                .build();
    }
}
