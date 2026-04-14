package ai.sagesource.opensagent.infrastructure.llm.openai;

import ai.sagesource.opensagent.base.utils.DotEnvUtils;
import ai.sagesource.opensagent.core.llm.client.LLMClient;
import ai.sagesource.opensagent.core.llm.client.LLMClientConfig;
import ai.sagesource.opensagent.core.llm.completion.*;
import ai.sagesource.opensagent.core.llm.message.MessageUtils;
import ai.sagesource.opensagent.core.llm.message.UserCompletionMessage;
import ai.sagesource.opensagent.core.llm.tool.ToolDefinition;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * OpenAI Completion集成测试
 * <p>
 * 需要真实API密钥，通过DotEnv获取
 *
 * @author: sage.xue
 * @time: 2026/4/14
 */
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class OpenAICompletionIntegrationTest {

    private static String apiKey;
    private static final String MODEL = "gpt-4o-mini";
    private static LLMCompletion completion;

    @BeforeAll
    static void setUp() {
        apiKey = DotEnvUtils.get("OPENAI_API_KEY");
        if (apiKey == null || apiKey.isEmpty()) {
            return;
        }
        LLMClientConfig config = LLMClientConfig.builder()
                .apiKey(apiKey)
                .model(MODEL)
                .build();
        LLMClient client = OpenAILLMClientFactory.createClient(config);
        completion = OpenAICompletionFactory.createCompletion(client);
    }

    @Test
    @DisplayName("同步调用 - 成功")
    void testComplete() {
        if (apiKey == null || apiKey.isEmpty()) {
            return;
        }
        CompletionRequest request = CompletionRequest.builder()
                .messages(List.of(UserCompletionMessage.of("Say 'hello' only")))
                .maxTokens(20)
                .build();

        CompletionResponse response = completion.complete(request);
        assertNotNull(response);
        assertNotNull(response.getMessage());
        assertNotNull(response.getFinishReason());
    }

    @Test
    @DisplayName("异步调用 - 成功")
    void testCompleteAsync() throws Exception {
        if (apiKey == null || apiKey.isEmpty()) {
            return;
        }
        CompletionRequest request = CompletionRequest.builder()
                .messages(List.of(UserCompletionMessage.of("Say 'hi' only")))
                .maxTokens(20)
                .build();

        CompletableFuture<CompletionResponse> future = completion.completeAsync(
                request, Executors.newSingleThreadExecutor());
        CompletionResponse response = future.get();
        assertNotNull(response);
        assertNotNull(response.getMessage());
    }

    @Test
    @DisplayName("流式调用 - 成功")
    void testStream() {
        if (apiKey == null || apiKey.isEmpty()) {
            return;
        }
        CompletionRequest request = CompletionRequest.builder()
                .messages(List.of(UserCompletionMessage.of("Count from 1 to 3")))
                .maxTokens(30)
                .build();

        StringBuilder sb = new StringBuilder();
        CompletionCancelToken token = completion.stream(request, chunk -> {
            if (chunk.getDeltaText() != null) {
                sb.append(chunk.getDeltaText());
            }
        });

        assertNotNull(token);
        assertFalse(sb.toString().isEmpty());
    }

    @Test
    @DisplayName("异步流式调用 - 成功")
    void testStreamAsync() throws Exception {
        if (apiKey == null || apiKey.isEmpty()) {
            return;
        }
        CompletionRequest request = CompletionRequest.builder()
                .messages(List.of(UserCompletionMessage.of("Say OK")))
                .maxTokens(10)
                .build();

        AtomicReference<String> result = new AtomicReference<>();
        CompletionCancelToken token = completion.streamAsync(request, chunk -> {
            if (chunk.getDeltaText() != null) {
                result.set(chunk.getDeltaText());
            }
        }, Executors.newSingleThreadExecutor());

        assertNotNull(token);
        // 等待流结束
        Thread.sleep(3000);
        assertNotNull(result.get());
    }

    @Test
    @DisplayName("使用兼容OpenAI的第三方接口进行同步调用 - 成功")
    void testCompleteWithCompatibleBaseUrl() {
        // 示例：通过环境变量获取兼容接口的配置
        String compatibleApiKey = DotEnvUtils.get("COMPATIBLE_OPENAI_API_KEY");
        String compatibleBaseUrl = DotEnvUtils.get("COMPATIBLE_OPENAI_BASE_URL");
        String compatibleModel = DotEnvUtils.get("COMPATIBLE_OPENAI_MODEL");

        // 跳过测试如果未配置兼容接口参数
        if (compatibleApiKey == null || compatibleApiKey.isEmpty()
                || compatibleBaseUrl == null || compatibleBaseUrl.isEmpty()
                || compatibleModel == null || compatibleModel.isEmpty()) {
            return;
        }

        // 使用快捷工厂方法构造带自定义BaseURL的客户端
        LLMClient client = OpenAILLMClientFactory.createClient(
                compatibleApiKey, compatibleModel, compatibleBaseUrl);
        LLMCompletion compatibleCompletion = OpenAICompletionFactory.createCompletion(client);

        CompletionRequest request = CompletionRequest.builder()
                .messages(List.of(UserCompletionMessage.of("你好")))
                .maxTokens(20)
                .build();

        CompletionResponse response = compatibleCompletion.complete(request);
        assertNotNull(response);
        assertNotNull(response.getMessage());
    }
}
