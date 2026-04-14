package ai.sagesource.opensagent.infrastructure.llm.openai;

import ai.sagesource.opensagent.core.llm.client.LLMClient;
import ai.sagesource.opensagent.core.llm.client.LLMClientConfig;
import ai.sagesource.opensagent.core.llm.exception.OpenSagentLLMException;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.core.Timeout;
import lombok.extern.slf4j.Slf4j;

import java.net.InetSocketAddress;
import java.net.Proxy;

/**
 * OpenAI LLM客户端实现
 *
 * @author: sage.xue
 * @time: 2026/4/13
 */
@Slf4j
public class OpenAILLMClient implements LLMClient {

    private final LLMClientConfig config;
    private final OpenAIClient openAIClient;

    /**
     * 构造方法，基于配置创建OpenAI客户端
     *
     * @param config 客户端配置
     * @throws OpenSagentLLMException 创建失败时抛出
     */
    public OpenAILLMClient(LLMClientConfig config) {
        this.config = config;
        try {
            this.openAIClient = buildClient(config);
            log.debug("> LLM | OpenAI客户端创建成功，模型: {} <", config.getModel());
        } catch (Exception e) {
            log.error("> LLM | 创建OpenAI客户端失败: {} <", e.getMessage(), e);
            throw new OpenSagentLLMException("创建OpenAI LLM客户端失败: " + e.getMessage(), e);
        }
    }

    /**
     * 构建OpenAI客户端
     *
     * @param config 配置参数
     * @return OpenAIClient实例
     */
    private OpenAIClient buildClient(LLMClientConfig config) {
        OpenAIOkHttpClient.Builder builder = OpenAIOkHttpClient.builder();

        // 设置API密钥
        if (config.getApiKey() != null && !config.getApiKey().isEmpty()) {
            builder.apiKey(config.getApiKey());
        }

        // 设置基础URL（用于代理或自定义端点）
        if (config.getBaseUrl() != null && !config.getBaseUrl().isEmpty()) {
            builder.baseUrl(config.getBaseUrl());
        }

        // 设置组织ID
        if (config.getOrganizationId() != null && !config.getOrganizationId().isEmpty()) {
            builder.organization(config.getOrganizationId());
        }

        // 设置项目ID
        if (config.getProjectId() != null && !config.getProjectId().isEmpty()) {
            builder.project(config.getProjectId());
        }

        // 设置超时
        if (config.getConnectTimeout() != null || config.getReadTimeout() != null) {
            Timeout timeout = Timeout.builder()
                    .connect(config.getConnectTimeout())
                    .read(config.getReadTimeout())
                    .build();
            builder.timeout(timeout);
        }

        // 设置代理
        if (config.getProxyHost() != null && !config.getProxyHost().isEmpty()
                && config.getProxyPort() != null) {
            Proxy proxy = new Proxy(Proxy.Type.HTTP,
                    new InetSocketAddress(config.getProxyHost(), config.getProxyPort()));
            builder.proxy(proxy);
        }

        return builder.build();
    }

    /**
     * 获取底层的OpenAI客户端（供内部使用）
     *
     * @return OpenAIClient实例
     */
    OpenAIClient getOpenAIClient() {
        return openAIClient;
    }

    @Override
    public LLMClientConfig getConfig() {
        return config;
    }

    @Override
    public boolean isHealthy() {
        try {
            // 通过简单的模型列表请求验证连接
            openAIClient.models().list();
            return true;
        } catch (Exception e) {
            log.warn("> LLM | OpenAI连接健康检查失败: {} <", e.getMessage());
            return false;
        }
    }

    @Override
    public void close() {
        // OpenAIClient不需要显式关闭，但保留接口以便扩展
        log.debug("> LLM | OpenAI客户端关闭 <");
    }
}
