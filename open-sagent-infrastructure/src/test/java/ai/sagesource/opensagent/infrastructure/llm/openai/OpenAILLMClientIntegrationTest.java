package ai.sagesource.opensagent.infrastructure.llm.openai;

import ai.sagesource.opensagent.base.utils.DotEnvUtils;
import ai.sagesource.opensagent.core.llm.client.LLMClient;
import ai.sagesource.opensagent.core.llm.client.LLMClientConfig;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.junit.jupiter.api.Assertions.*;

/**
 * OpenAILLMClient集成测试
 * <p>
 * 需要真实API密钥，通过DotEnv获取
 *
 * @author: sage.xue
 * @time: 2026/4/13
 */
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class OpenAILLMClientIntegrationTest {

    private static String apiKey;
    private static final String MODEL = "gpt-4o-mini";

    @BeforeAll
    static void setUp() {
        // 通过DotEnv获取敏感配置
        apiKey = DotEnvUtils.get("OPENAI_API_KEY");
    }

    @Test
    @DisplayName("连接健康检查 - 成功")
    void testIsHealthy() {
        // 跳过测试如果未配置API密钥
        if (apiKey == null || apiKey.isEmpty()) {
            return;
        }

        // 构造客户端
        LLMClient client = OpenAILLMClientFactory.createClient(apiKey, MODEL);

        // 执行并验证
        assertTrue(client.isHealthy(), "连接应正常");
    }

    @Test
    @DisplayName("完整配置创建客户端 - 成功")
    void testCreateClientWithFullConfig() {
        // 跳过测试如果未配置API密钥
        if (apiKey == null || apiKey.isEmpty()) {
            return;
        }

        // 构造完整配置
        LLMClientConfig config = LLMClientConfig.builder()
                .apiKey(apiKey)
                .model(MODEL)
                .baseUrl("https://api.openai.com/v1")
                .build();

        // 执行
        LLMClient client = OpenAILLMClientFactory.createClient(config);

        // 验证
        assertNotNull(client);
        assertTrue(client.isHealthy());
    }

    @Test
    @DisplayName("使用兼容OpenAI的第三方BaseURL创建客户端 - 成功")
    void testCreateClientWithCompatibleBaseUrl() {
        // 示例：通过环境变量获取兼容接口的密钥和地址
        String compatibleApiKey = DotEnvUtils.get("COMPATIBLE_OPENAI_API_KEY");
        String compatibleBaseUrl = DotEnvUtils.get("COMPATIBLE_OPENAI_BASE_URL");
        String compatibleModel = DotEnvUtils.get("COMPATIBLE_OPENAI_MODEL");

        // 跳过测试如果未配置兼容接口参数
        if (compatibleApiKey == null || compatibleApiKey.isEmpty()
                || compatibleBaseUrl == null || compatibleBaseUrl.isEmpty()
                || compatibleModel == null || compatibleModel.isEmpty()) {
            return;
        }

        // 构造带自定义BaseURL的客户端（如 DeepSeek、SiliconFlow 等）
        LLMClient client = OpenAILLMClientFactory.createClient(
                compatibleApiKey, compatibleModel, compatibleBaseUrl);

        // 验证
        assertNotNull(client);
        assertEquals(compatibleBaseUrl, client.getConfig().getBaseUrl());
    }
}
