package ai.sagesource.opensagent.core.llm.message;

import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 系统消息实现
 * <p>
 * 用于设置AI助手的行为、角色和上下文
 *
 * @author: sage.xue
 * @time: 2026/4/13
 */
@Data
@Builder
public class SystemCompletionMessage implements CompletionMessage {

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
        return MessageRole.SYSTEM;
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
     * 创建纯文本系统消息（便捷方法）
     *
     * @param text 系统提示文本
     * @return SystemCompletionMessage实例
     */
    public static SystemCompletionMessage of(String text) {
        return SystemCompletionMessage.builder()
                .contents(new ArrayList<>(List.of(TextContent.builder().text(text).build())))
                .build();
    }
}
