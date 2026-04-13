package ai.sagesource.opensagent.core.llm.message;

/**
 * 消息内容类型枚举
 * <p>
 * 定义消息内容的媒体类型
 *
 * @author: sage.xue
 * @time: 2026/4/13
 */
public enum ContentType {

    /**
     * 纯文本内容
     */
    TEXT,

    /**
     * 图片内容
     */
    IMAGE,

    /**
     * 文件内容（文档、PDF等）
     */
    FILE
}
