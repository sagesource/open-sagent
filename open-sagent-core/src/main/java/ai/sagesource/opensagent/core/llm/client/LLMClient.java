package ai.sagesource.opensagent.core.llm.client;

/**
 * LLM客户端抽象接口
 * <p>
 * 定义与LLM厂商交互的统一接口，屏蔽底层实现差异。
 * 不同厂商的实现通过具体的工厂类区分（如OpenAILLMClientFactory、AnthropicLLMClientFactory等）
 *
 * @author: sage.xue
 * @time: 2026/4/13
 */
public interface LLMClient {

    /**
     * 获取客户端配置
     *
     * @return 配置对象
     */
    LLMClientConfig getConfig();

    /**
     * 测试连接是否可用
     *
     * @return true表示连接正常
     */
    boolean isHealthy();

    /**
     * 关闭客户端，释放资源
     */
    void close();
}
