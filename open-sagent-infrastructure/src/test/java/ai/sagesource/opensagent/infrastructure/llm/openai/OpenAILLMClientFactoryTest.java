package ai.sagesource.opensagent.infrastructure.llm.openai;

import ai.sagesource.opensagent.core.llm.client.LLMClient;
import ai.sagesource.opensagent.core.llm.client.LLMClientConfig;
import ai.sagesource.opensagent.core.llm.exception.OpenSagentLLMException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * OpenAILLMClientFactory单元测试
 *
 * @author: sage.xue
 * @time: 2026/4/13
 */
class OpenAILLMClientFactoryTest {

    @Test
    @DisplayName("使用有效配置创建客户端 - 成功")
    void testCreateClientWithValidConfig() {
        // 构造配置
        LLMClientConfig config = LLMClientConfig.builder()
                .apiKey("test-api-key")
                .model("gpt-4")
                .build();

        // 执行
        LLMClient client = OpenAILLMClientFactory.createClient(config);

        // 验证
        assertNotNull(client, "客户端不应为空");
        assertNotNull(client.getConfig(), "配置不应为空");
        assertEquals("gpt-4", client.getConfig().getModel(), "模型名称应一致");
    }

    @Test
    @DisplayName("使用快速方法创建客户端 - 成功")
    void testCreateClientWithQuickMethod() {
        // 执行
        LLMClient client = OpenAILLMClientFactory.createClient("test-api-key", "gpt-3.5-turbo");

        // 验证
        assertNotNull(client, "客户端不应为空");
        assertEquals("gpt-3.5-turbo", client.getConfig().getModel(), "模型名称应一致");
    }

    @Test
    @DisplayName("配置为null - 抛出异常")
    void testCreateClientWithNullConfig() {
        // 执行并验证
        OpenSagentLLMException exception = assertThrows(
                OpenSagentLLMException.class,
                () -> OpenAILLMClientFactory.createClient(null),
                "配置为null时应抛出异常"
        );
        assertTrue(exception.getMessage().contains("不能为空"));
    }

    @Test
    @DisplayName("API密钥为空 - 抛出异常")
    void testCreateClientWithEmptyApiKey() {
        // 构造配置
        LLMClientConfig config = LLMClientConfig.builder()
                .apiKey("")
                .model("gpt-4")
                .build();

        // 执行并验证
        OpenSagentLLMException exception = assertThrows(
                OpenSagentLLMException.class,
                () -> OpenAILLMClientFactory.createClient(config),
                "API密钥为空时应抛出异常"
        );
        assertTrue(exception.getMessage().contains("API密钥不能为空"));
    }

    @Test
    @DisplayName("模型名称为空 - 抛出异常")
    void testCreateClientWithEmptyModel() {
        // 构造配置
        LLMClientConfig config = LLMClientConfig.builder()
                .apiKey("test-api-key")
                .model("")
                .build();

        // 执行并验证
        OpenSagentLLMException exception = assertThrows(
                OpenSagentLLMException.class,
                () -> OpenAILLMClientFactory.createClient(config),
                "模型名称为空时应抛出异常"
        );
        assertTrue(exception.getMessage().contains("模型名称不能为空"));
    }
}
