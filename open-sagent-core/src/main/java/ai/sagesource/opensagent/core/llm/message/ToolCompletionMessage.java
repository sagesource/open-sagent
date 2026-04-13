package ai.sagesource.opensagent.core.llm.message;

import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 工具消息实现
 * <p>
 * 表示工具调用的结果消息
 *
 * @author: sage.xue
 * @time: 2026/4/13
 */
@Data
@Builder
public class ToolCompletionMessage implements CompletionMessage {

    /**
     * 消息ID
     */
    private String messageId;

    /**
     * 工具调用ID
     */
    private String toolCallId;

    /**
     * 工具名称
     */
    private String toolName;

    /**
     * 消息内容列表
     */
    @Builder.Default
    private List<MessageContent> contents = new ArrayList<>();

    @Override
    public MessageRole getRole() {
        return MessageRole.TOOL;
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
     * 创建工具结果消息（便捷方法）
     *
     * @param toolCallId 工具调用ID
     * @param toolName   工具名称
     * @param result     工具执行结果文本
     * @return ToolCompletionMessage实例
     */
    public static ToolCompletionMessage of(String toolCallId, String toolName, String result) {
        return ToolCompletionMessage.builder()
                .toolCallId(toolCallId)
                .toolName(toolName)
                .contents(new ArrayList<>(List.of(TextContent.builder().text(result).build())))
                .build();
    }
}
