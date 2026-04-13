package ai.sagesource.opensagent.core.llm.message;

import java.util.ArrayList;
import java.util.List;

/**
 * 消息工具类
 * <p>
 * 提供消息创建、转换等便捷方法
 *
 * @author: sage.xue
 * @time: 2026/4/13
 */
public final class MessageUtils {

    private MessageUtils() {
        // 工具类禁止实例化
    }

    /**
     * 创建用户文本消息
     *
     * @param text 文本内容
     * @return UserCompletionMessage
     */
    public static CompletionMessage user(String text) {
        return UserCompletionMessage.of(text);
    }

    /**
     * 创建系统文本消息
     *
     * @param text 文本内容
     * @return SystemCompletionMessage
     */
    public static CompletionMessage system(String text) {
        return SystemCompletionMessage.of(text);
    }

    /**
     * 创建助手文本消息
     *
     * @param text 文本内容
     * @return AssistantCompletionMessage
     */
    public static CompletionMessage assistant(String text) {
        return AssistantCompletionMessage.of(text);
    }

    /**
     * 创建开发者文本消息
     *
     * @param text 文本内容
     * @return DeveloperCompletionMessage
     */
    public static CompletionMessage developer(String text) {
        return DeveloperCompletionMessage.of(text);
    }

    /**
     * 创建工具结果消息
     *
     * @param toolCallId 工具调用ID
     * @param toolName   工具名称
     * @param result     工具执行结果
     * @return ToolCompletionMessage
     */
    public static CompletionMessage tool(String toolCallId, String toolName, String result) {
        return ToolCompletionMessage.of(toolCallId, toolName, result);
    }

    /**
     * 创建包含图片的用户消息
     *
     * @param text     文本内容
     * @param imageUrl 图片URL
     * @return UserCompletionMessage
     */
    public static UserCompletionMessage userWithImage(String text, String imageUrl) {
        List<MessageContent> contents = new ArrayList<>();
        contents.add(TextContent.builder().text(text).build());
        contents.add(ImageContent.builder().url(imageUrl).build());
        return UserCompletionMessage.builder().contents(contents).build();
    }

    /**
     * 创建包含BASE64图片的用户消息
     *
     * @param text       文本内容
     * @param base64Data BASE64编码的图片数据
     * @param mimeType   图片MIME类型
     * @return UserCompletionMessage
     */
    public static UserCompletionMessage userWithImageBase64(String text, String base64Data, String mimeType) {
        List<MessageContent> contents = new ArrayList<>();
        contents.add(TextContent.builder().text(text).build());
        contents.add(ImageContent.builder()
                .base64Data(base64Data)
                .mimeType(mimeType)
                .build());
        return UserCompletionMessage.builder().contents(contents).build();
    }

    /**
     * 将消息列表转换为纯文本摘要
     *
     * @param messages 消息列表
     * @return 文本摘要
     */
    public static String toTextSummary(List<CompletionMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (CompletionMessage message : messages) {
            sb.append("[").append(message.getRole()).append("]: ");
            sb.append(message.getTextContent());
            sb.append("\n");
        }
        return sb.toString().trim();
    }
}
