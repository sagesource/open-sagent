package ai.sagesource.opensagent.web.config;

import ai.sagesource.opensagent.core.llm.client.LLMClient;
import ai.sagesource.opensagent.core.llm.client.LLMClientConfig;
import ai.sagesource.opensagent.core.llm.completion.LLMCompletion;
import ai.sagesource.opensagent.infrastructure.llm.openai.OpenAILLMClient;
import ai.sagesource.opensagent.infrastructure.llm.openai.OpenAICompletionFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Agent初始化配置
 * <p>
 * 从Spring配置文件（支持DotEnv覆盖）初始化LLMClient和Agent配置
 *
 * @author: sage.xue
 * @time: 2026/4/26
 */
@Slf4j
@Configuration
public class AgentBeanConfig {

    @Value("${sagent.llm.api-key}")
    private String apiKey;

    @Value("${sagent.llm.base-url:https://api.openai.com}")
    private String baseUrl;

    @Value("${sagent.llm.model:gpt-4o-mini}")
    private String model;

    @Value("${sagent.agent.simple.temperature:0.7}")
    private Double simpleTemperature;

    @Value("${sagent.agent.simple.max-tokens:2048}")
    private Integer simpleMaxTokens;

    @Value("${sagent.agent.smart.temperature:0.7}")
    private Double smartTemperature;

    @Value("${sagent.agent.smart.max-tokens:4096}")
    private Integer smartMaxTokens;

    @Value("${sagent.agent.smart.max-iterations:10}")
    private Integer smartMaxIterations;

    @Value("${sagent.title-agent.temperature:0.5}")
    private Double titleTemperature;

    @Value("${sagent.title-agent.max-tokens:100}")
    private Integer titleMaxTokens;

    @Bean
    public LLMClient llmClient() {
        LLMClientConfig config = LLMClientConfig.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .build();
        return new OpenAILLMClient(config);
    }

    @Bean
    public LLMCompletion llmCompletion(LLMClient llmClient) {
        return OpenAICompletionFactory.createCompletion(llmClient);
    }

    @Bean
    public ai.sagesource.opensagent.core.agent.AgentConfig simpleAgentConfig() {
        return ai.sagesource.opensagent.core.agent.AgentConfig.builder()
                .enableTools(false)
                .temperature(simpleTemperature)
                .maxTokens(simpleMaxTokens)
                .build();
    }

    @Bean
    public ai.sagesource.opensagent.core.agent.AgentConfig smartAgentConfig() {
        return ai.sagesource.opensagent.core.agent.AgentConfig.builder()
                .enableTools(true)
                .temperature(smartTemperature)
                .maxTokens(smartMaxTokens)
                .maxIterations(smartMaxIterations)
                .build();
    }

    @Bean
    public ai.sagesource.opensagent.core.agent.AgentConfig titleAgentConfig() {
        return ai.sagesource.opensagent.core.agent.AgentConfig.builder()
                .enableTools(false)
                .temperature(titleTemperature)
                .maxTokens(titleMaxTokens)
                .build();
    }
}
