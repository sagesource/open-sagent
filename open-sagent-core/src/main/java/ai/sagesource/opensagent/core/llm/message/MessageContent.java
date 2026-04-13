package ai.sagesource.opensagent.core.llm.message;

/**
 * 消息内容抽象接口
 * <p>
 * 定义消息内容的统一接口，支持文本、图片、文件等多种类型
 *
 * @author: sage.xue
 * @time: 2026/4/13
 */
public interface MessageContent {

    /**
     * 获取内容类型
     *
     * @return 内容类型枚举
     */
    ContentType getType();

    /**
     * 获取纯文本表示
     * <p>
     * 对于非文本内容，返回描述性文本
     *
     * @return 文本内容
     */
    String getText();
}
