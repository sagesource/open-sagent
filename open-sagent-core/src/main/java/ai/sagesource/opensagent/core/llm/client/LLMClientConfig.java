package ai.sagesource.opensagent.core.llm.client;

import lombok.Builder;
import lombok.Data;

import java.time.Duration;

/**
 * LLM客户端配置参数类
 * <p>
 * 通用配置，各厂商实现根据需要使用相应字段
 *
 * @author: sage.xue
 * @time: 2026/4/13
 */
@Data
@Builder
public class LLMClientConfig {

    /**
     * API密钥
     */
    private String apiKey;

    /**
     * 基础URL（可选，用于自定义端点或代理）
     */
    private String baseUrl;

    /**
     * 模型名称
     */
    private String model;

    /**
     * 连接超时时间
     */
    @Builder.Default
    private Duration connectTimeout = Duration.ofSeconds(10);

    /**
     * 读取超时时间
     */
    @Builder.Default
    private Duration readTimeout = Duration.ofSeconds(60);

    /**
     * 代理主机（可选）
     */
    private String proxyHost;

    /**
     * 代理端口（可选）
     */
    private Integer proxyPort;

    /**
     * 组织ID（可选，用于OpenAI等）
     */
    private String organizationId;

    /**
     * 项目ID（可选，用于OpenAI等）
     */
    private String projectId;
}
