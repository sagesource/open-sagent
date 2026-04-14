package ai.sagesource.opensagent.infrastructure.llm.openai;

import ai.sagesource.opensagent.core.llm.client.LLMClient;
import ai.sagesource.opensagent.core.llm.client.LLMClientConfig;
import ai.sagesource.opensagent.core.llm.completion.LLMCompletion;
import ai.sagesource.opensagent.core.llm.exception.OpenSagentLLMException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * OpenAI Completion工厂单元测试
 *
 * @author: sage.xue
 * @time: 2026/4/14
 */
class OpenAICompletionFactoryTest {

    @Test
    @DisplayName("使用OpenAILLMClient创建Completion - 成功")
    void testCreateWithOpenAIClient() {
        LLMClientConfig config = LLMClientConfig.builder()
                .apiKey("test-key")
                .model("gpt-4")
                .build();
        OpenAILLMClient client = new OpenAILLMClient(config);

        LLMCompletion completion = OpenAICompletionFactory.createCompletion(client);
        assertNotNull(completion);
    }

    @Test
    @DisplayName("使用LLMClient接口创建Completion - 成功")
    void testCreateWithLLMClientInterface() {
        LLMClientConfig config = LLMClientConfig.builder()
                .apiKey("test-key")
                .model("gpt-4")
                .build();
        LLMClient client = new OpenAILLMClient(config);

        LLMCompletion completion = OpenAICompletionFactory.createCompletion(client);
        assertNotNull(completion);
    }

    @Test
    @DisplayName("client为null - 抛出异常")
    void testCreateWithNullClient() {
        OpenSagentLLMException exception = assertThrows(
                OpenSagentLLMException.class,
                () -> OpenAICompletionFactory.createCompletion((LLMClient) null)
        );
        assertTrue(exception.getMessage().contains("不能为空"));
    }

    @Test
    @DisplayName("非OpenAILLMClient类型 - 抛出异常")
    void testCreateWithWrongClientType() {
        LLMClient mockClient = new LLMClient() {
            @Override
            public LLMClientConfig getConfig() {
                return null;
            }

            @Override
            public boolean isHealthy() {
                return false;
            }

            @Override
            public void close() {
            }
        };

        OpenSagentLLMException exception = assertThrows(
                OpenSagentLLMException.class,
                () -> OpenAICompletionFactory.createCompletion(mockClient)
        );
        assertTrue(exception.getMessage().contains("OpenAILLMClient"));
    }

    @Test
    @DisplayName("使用带BaseURL的OpenAILLMClient创建Completion - 成功")
    void testCreateWithBaseUrlClient() {
        LLMClientConfig config = LLMClientConfig.builder()
                .apiKey("test-key")
                .model("deepseek-chat")
                .baseUrl("https://api.deepseek.com/v1")
                .build();
        OpenAILLMClient client = new OpenAILLMClient(config);

        LLMCompletion completion = OpenAICompletionFactory.createCompletion(client);
        assertNotNull(completion);
    }

    @Test
    @DisplayName("使用快捷方法构建带BaseURL的客户端创建Completion - 成功")
    void testCreateCompletionWithBaseUrlQuickMethod() {
        LLMClient client = OpenAILLMClientFactory.createClient(
                "test-key", "deepseek-chat", "https://api.deepseek.com/v1");

        LLMCompletion completion = OpenAICompletionFactory.createCompletion(client);
        assertNotNull(completion);
        assertEquals("https://api.deepseek.com/v1", client.getConfig().getBaseUrl());
    }
}
