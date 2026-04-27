package ai.sagesource.opensagent.web.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

/**
 * Prompt 配置绑定类
 * <p>
 * 绑定 application.yml 中 sagent.agent.*.prompt-path 和 sagent.agent.*.prompt-vars 配置
 *
 * @author: sage.xue
 * @time: 2026/4/27
 */
@Data
@ConfigurationProperties(prefix = "sagent.agent")
public class PromptProperties {

    private AgentPromptConfig simple = new AgentPromptConfig();
    private AgentPromptConfig smart = new AgentPromptConfig();
    private AgentPromptConfig title = new AgentPromptConfig();

    @Data
    public static class AgentPromptConfig {
        private String promptPath;
        private Map<String, String> promptVars = new HashMap<>();
    }
}
