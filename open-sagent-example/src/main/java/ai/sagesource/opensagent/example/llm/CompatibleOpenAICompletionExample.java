package ai.sagesource.opensagent.example.llm;

import ai.sagesource.opensagent.base.utils.DotEnvUtils;
import ai.sagesource.opensagent.core.llm.client.LLMClient;
import ai.sagesource.opensagent.core.llm.client.LLMClientConfig;
import ai.sagesource.opensagent.core.llm.completion.CompletionRequest;
import ai.sagesource.opensagent.core.llm.completion.CompletionResponse;
import ai.sagesource.opensagent.core.llm.completion.LLMCompletion;
import ai.sagesource.opensagent.core.llm.message.UserCompletionMessage;
import ai.sagesource.opensagent.infrastructure.llm.openai.OpenAICompletionFactory;
import ai.sagesource.opensagent.infrastructure.llm.openai.OpenAILLMClientFactory;

import java.time.Duration;

/**
 * 调用兼容OpenAI接口的第三方大模型示例
 * <p>
 * 适用于 DeepSeek、SiliconFlow、Azure OpenAI、VolcEngine 等提供 OpenAI 兼容接口的厂商。
 * 通过指定 baseUrl，即可复用 OpenAI 的 Client 和 Completion 实现。
 *
 * @author: sage.xue
 * @time: 2026/4/14
 */
public class CompatibleOpenAICompletionExample {

    public static void main(String[] args) {
        // ====== 方式1：使用 LLMClientConfig 构建（推荐） ======
        LLMClientConfig config = LLMClientConfig.builder()
                .apiKey(DotEnvUtils.get("COMPATIBLE_OPENAI_API_KEY"))
                .model(DotEnvUtils.get("COMPATIBLE_OPENAI_MODEL"))
                .baseUrl(DotEnvUtils.get("COMPATIBLE_OPENAI_BASE_URL"))
                .connectTimeout(Duration.ofSeconds(60))
                .build();

        LLMClient     client     = OpenAILLMClientFactory.createClient(config);
        LLMCompletion completion = OpenAICompletionFactory.createCompletion(client);

        CompletionRequest request = CompletionRequest.builder()
                .messages(java.util.List.of(UserCompletionMessage.of("通过API调用千问时，超时时间的最佳配置是什么？")))
                .maxTokens(65536)
                .build();

        CompletionResponse response = completion.complete(request);
        System.out.println("方式1回复: " + response.getMessage().getTextContent());

        // ====== 方式2：使用快捷工厂方法构建 ======
        LLMClient quickClient = OpenAILLMClientFactory.createClient(
                DotEnvUtils.get("COMPATIBLE_OPENAI_API_KEY"),
                DotEnvUtils.get("COMPATIBLE_OPENAI_MODEL"),
                DotEnvUtils.get("COMPATIBLE_OPENAI_BASE_URL")
        );
        LLMCompletion quickCompletion = OpenAICompletionFactory.createCompletion(quickClient);

        CompletionResponse quickResponse = quickCompletion.complete(request);
        System.out.println("方式2回复: " + quickResponse.getMessage().getTextContent());
    }
}
