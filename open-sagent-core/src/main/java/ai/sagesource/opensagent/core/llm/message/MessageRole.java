package ai.sagesource.opensagent.core.llm.message;

/**
 * 消息角色枚举
 * <p>
 * 定义LLM对话中参与者的角色类型
 *
 * @author: sage.xue
 * @time: 2026/4/13
 */
public enum MessageRole {

    /**
     * 系统角色：定义AI助手的行为和上下文
     */
    SYSTEM,

    /**
     * 用户角色：发送请求的人
     */
    USER,

    /**
     * 助手角色：AI模型的回复
     */
    ASSISTANT,

    /**
     * 开发者角色：用于向模型提供指令（OpenAI o1系列模型支持）
     */
    DEVELOPER,

    /**
     * 工具角色：工具调用的结果
     */
    TOOL
}
