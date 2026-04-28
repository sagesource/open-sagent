package ai.sagesource.opensagent.web.config;

import ai.sagesource.opensagent.infrastructure.agent.prompt.DefaultPromptTemplate;
import ai.sagesource.opensagent.core.agent.prompt.PromptRenderContext;
import ai.sagesource.opensagent.core.agent.prompt.PromptTemplate;
import ai.sagesource.opensagent.core.agent.prompt.PromptTemplateLoader;
import ai.sagesource.opensagent.core.llm.client.LLMClient;
import ai.sagesource.opensagent.core.llm.client.LLMClientConfig;
import ai.sagesource.opensagent.core.llm.completion.LLMCompletion;
import ai.sagesource.opensagent.infrastructure.agent.prompt.ClasspathPromptTemplateLoader;
import ai.sagesource.opensagent.infrastructure.agent.prompt.FileSystemPromptTemplateLoader;
import ai.sagesource.opensagent.infrastructure.llm.openai.OpenAILLMClient;
import ai.sagesource.opensagent.infrastructure.llm.openai.OpenAICompletionFactory;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

/**
 * Agent初始化配置
 * <p>
 * 从Spring配置文件（支持DotEnv覆盖）初始化LLMClient和Agent配置
 * <p>
 * 每个Agent使用独立的LLMClient和LLMCompletion，支持不同模型和API密钥配置
 *
 * @author: sage.xue
 * @time: 2026/4/26
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(PromptProperties.class)
public class AgentBeanConfig {

    // ========== 全局默认LLM配置（作为各Agent的fallback默认值） ==========
    @Value("${sagent.llm.api-key:}")
    private String defaultApiKey;

    @Value("${sagent.llm.base-url:https://api.openai.com}")
    private String defaultBaseUrl;

    // ========== SimpleAgent LLM配置 ==========
    @Value("${sagent.agent.simple.api-key:${sagent.llm.api-key:}}")
    private String simpleApiKey;

    @Value("${sagent.agent.simple.base-url:${sagent.llm.base-url:https://api.openai.com}}")
    private String simpleBaseUrl;

    @Value("${sagent.agent.simple.model:gpt-4o-mini}")
    private String simpleModel;

    @Value("${sagent.agent.simple.temperature:0.7}")
    private Double simpleTemperature;

    @Value("${sagent.agent.simple.max-tokens:2048}")
    private Integer simpleMaxTokens;

    // ========== ReActAgent(Smart) LLM配置 ==========
    @Value("${sagent.agent.smart.api-key:${sagent.llm.api-key:}}")
    private String smartApiKey;

    @Value("${sagent.agent.smart.base-url:${sagent.llm.base-url:https://api.openai.com}}")
    private String smartBaseUrl;

    @Value("${sagent.agent.smart.model:gpt-4o-mini}")
    private String smartModel;

    @Value("${sagent.agent.smart.temperature:0.7}")
    private Double smartTemperature;

    @Value("${sagent.agent.smart.max-tokens:4096}")
    private Integer smartMaxTokens;

    @Value("${sagent.agent.smart.max-iterations:10}")
    private Integer smartMaxIterations;

    // ========== TitleAgent LLM配置 ==========
    @Value("${sagent.agent.title.api-key:${sagent.llm.api-key:}}")
    private String titleApiKey;

    @Value("${sagent.agent.title.base-url:${sagent.llm.base-url:https://api.openai.com}}")
    private String titleBaseUrl;

    @Value("${sagent.agent.title.model:gpt-4o-mini}")
    private String titleModel;

    @Value("${sagent.agent.title.temperature:0.5}")
    private Double titleTemperature;

    @Value("${sagent.agent.title.max-tokens:100}")
    private Integer titleMaxTokens;

    @Resource
    private PromptProperties promptProperties;

    private LLMClient createLLMClient(String apiKey, String baseUrl, String model) {
        String actualApiKey = (apiKey != null && !apiKey.isEmpty()) ? apiKey : defaultApiKey;
        String actualBaseUrl = (baseUrl != null && !baseUrl.isEmpty()) ? baseUrl : defaultBaseUrl;
        LLMClientConfig config = LLMClientConfig.builder()
                .apiKey(actualApiKey)
                .baseUrl(actualBaseUrl)
                .model(model)
                .build();
        return new OpenAILLMClient(config);
    }

    @Bean
    public LLMCompletion simpleCompletion() {
        LLMClient client = createLLMClient(simpleApiKey, simpleBaseUrl, simpleModel);
        return OpenAICompletionFactory.createCompletion(client);
    }

    @Bean
    public LLMCompletion smartCompletion() {
        LLMClient client = createLLMClient(smartApiKey, smartBaseUrl, smartModel);
        return OpenAICompletionFactory.createCompletion(client);
    }

    @Bean
    public LLMCompletion titleCompletion() {
        LLMClient client = createLLMClient(titleApiKey, titleBaseUrl, titleModel);
        return OpenAICompletionFactory.createCompletion(client);
    }

    // ========== PromptTemplate Bean ==========

    @Bean
    public PromptTemplate simplePromptTemplate() {
        return loadPrompt(
                promptProperties.getSimple().getPromptPath(),
                promptProperties.getSimple().getPromptVars(),
                "你是一个 helpful 的AI助手，请尽力回答用户的问题。"
        );
    }

    @Bean
    public PromptTemplate smartPromptTemplate() {
        return loadPrompt(
                promptProperties.getSmart().getPromptPath(),
                promptProperties.getSmart().getPromptVars(),
                "你是一个 helpful 的AI助手，请尽力回答用户的问题。你可以使用工具来辅助完成任务。"
        );
    }

    @Bean
    public PromptTemplate titlePromptTemplate() {
        return loadPrompt(
                promptProperties.getTitle().getPromptPath(),
                promptProperties.getTitle().getPromptVars(),
                """
                你是一个对话标题生成助手。请根据用户的输入内容，生成一个简短的对话标题（不超过10个字）。
                只输出标题文本，不要添加任何解释、引号或额外内容。
                """
        );
    }

    private PromptTemplate loadPrompt(String path, Map<String, String> vars, String fallback) {
        if (path == null || path.isBlank()) {
            return new DefaultPromptTemplate(fallback);
        }

        PromptTemplateLoader loader;
        String templateName;

        if (path.startsWith("classpath:")) {
            loader = new ClasspathPromptTemplateLoader();
            templateName = path.substring("classpath:".length());
        } else if (path.startsWith("/") || path.contains(":/") || path.contains(":\\")) {
            Path dir = Paths.get(path).getParent();
            templateName = Paths.get(path).getFileName().toString();
            loader = new FileSystemPromptTemplateLoader(dir);
        } else {
            loader = new ClasspathPromptTemplateLoader();
            templateName = path;
        }

        try {
            PromptTemplate template = loader.load(templateName);
            String rendered = template.render(PromptRenderContext.of(vars));
            log.info("> AgentBeanConfig | 加载Prompt模板成功: {} <", path);
            return new DefaultPromptTemplate(rendered);
        } catch (Exception e) {
            log.warn("> AgentBeanConfig | 加载Prompt模板失败[{}]，使用默认Prompt <", path);
            return new DefaultPromptTemplate(fallback);
        }
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
