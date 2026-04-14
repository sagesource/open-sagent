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
                .build();

        LLMClient     client     = OpenAILLMClientFactory.createClient(config);
        LLMCompletion completion = OpenAICompletionFactory.createCompletion(client);

        CompletionRequest request = CompletionRequest.builder()
                .messages(java.util.List.of(UserCompletionMessage.of("请用一句话介绍自己")))
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
