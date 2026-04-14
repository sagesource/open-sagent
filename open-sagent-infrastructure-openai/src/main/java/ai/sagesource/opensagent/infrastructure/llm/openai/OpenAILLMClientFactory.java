package ai.sagesource.opensagent.infrastructure.llm.openai;

import ai.sagesource.opensagent.core.llm.client.LLMClient;
import ai.sagesource.opensagent.core.llm.client.LLMClientConfig;
import ai.sagesource.opensagent.core.llm.exception.OpenSagentLLMException;

/**
 * OpenAI LLM客户端工厂类
 *
 * @author: sage.xue
 * @time: 2026/4/13
 */
public class OpenAILLMClientFactory {

    /**
     * 创建OpenAI客户端
     *
     * @param config 客户端配置
     * @return LLMClient实例
     * @throws OpenSagentLLMException 当配置无效或创建失败时抛出
     */
    public static LLMClient createClient(LLMClientConfig config) {
        validateConfig(config);
        return new OpenAILLMClient(config);
    }

    /**
     * 验证配置参数
     *
     * @param config 配置对象
     * @throws OpenSagentLLMException 配置无效时抛出
     */
    private static void validateConfig(LLMClientConfig config) {
        if (config == null) {
            throw new OpenSagentLLMException("LLMClientConfig不能为空");
        }
        if (config.getApiKey() == null || config.getApiKey().isEmpty()) {
            throw new OpenSagentLLMException("API密钥不能为空");
        }
        if (config.getModel() == null || config.getModel().isEmpty()) {
            throw new OpenSagentLLMException("模型名称不能为空");
        }
    }

    /**
     * 使用默认配置快速创建客户端
     *
     * @param apiKey API密钥
     * @param model  模型名称
     * @return LLMClient实例
     */
    public static LLMClient createClient(String apiKey, String model) {
        LLMClientConfig config = LLMClientConfig.builder()
                .apiKey(apiKey)
                .model(model)
                .build();
        return createClient(config);
    }

    /**
     * 使用自定义BaseURL快速创建客户端（用于兼容OpenAI的第三方接口）
     *
     * @param apiKey  API密钥
     * @param model   模型名称
     * @param baseUrl 自定义基础URL（如 https://api.deepseek.com/v1）
     * @return LLMClient实例
     */
    public static LLMClient createClient(String apiKey, String model, String baseUrl) {
        LLMClientConfig config = LLMClientConfig.builder()
                .apiKey(apiKey)
                .model(model)
                .baseUrl(baseUrl)
                .build();
        return createClient(config);
    }
}
