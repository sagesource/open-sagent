package ai.sagesource.opensagent.core.llm.message;

import java.util.List;

/**
 * 消息基类抽象接口
 * <p>
 * 定义LLM对话消息的通用接口，所有具体消息类型都实现此接口
 *
 * @author: sage.xue
 * @time: 2026/4/13
 */
public interface CompletionMessage {

    /**
     * 获取消息角色
     *
     * @return 消息角色枚举
     */
    MessageRole getRole();

    /**
     * 获取消息内容列表
     * <p>
     * 支持多模态内容，列表中的每个元素代表一种内容（文本、图片、文件等）
     *
     * @return 内容列表
     */
    List<MessageContent> getContents();

    /**
     * 获取消息ID（可选）
     *
     * @return 消息唯一标识，可能为null
     */
    String getMessageId();

    /**
     * 获取纯文本内容
     * <p>
     * 将所有内容拼接成纯文本表示
     *
     * @return 纯文本内容
     */
    default String getTextContent() {
        if (getContents() == null || getContents().isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (MessageContent content : getContents()) {
            sb.append(content.getText());
        }
        return sb.toString();
    }

    /**
     * 添加内容
     *
     * @param content 要添加的内容
     */
    void addContent(MessageContent content);

    /**
     * 添加文本内容（便捷方法）
     *
     * @param text 文本内容
     */
    default void addText(String text) {
        addContent(TextContent.builder().text(text).build());
    }
}
