package ai.sagesource.opensagent.infrastructure.llm.openai;

import ai.sagesource.opensagent.core.llm.client.LLMClient;
import ai.sagesource.opensagent.core.llm.completion.LLMCompletion;
import ai.sagesource.opensagent.core.llm.exception.OpenSagentLLMException;

/**
 * OpenAI Completion工厂类
 *
 * @author: sage.xue
 * @time: 2026/4/14
 */
public class OpenAICompletionFactory {

    /**
     * 基于LLMClient创建Completion实例
     *
     * @param client LLMClient（必须是OpenAILLMClient实例）
     * @return LLMCompletion实例
     * @throws OpenSagentLLMException 当client类型不匹配时抛出
     */
    public static LLMCompletion createCompletion(LLMClient client) {
        if (client == null) {
            throw new OpenSagentLLMException("LLMClient不能为空");
        }
        if (!(client instanceof OpenAILLMClient openAIClient)) {
            throw new OpenSagentLLMException("LLMClient必须是OpenAILLMClient类型");
        }
        return new OpenAICompletion(openAIClient.getOpenAIClient(), openAIClient.getConfig().getModel());
    }

    /**
     * 直接基于OpenAILLMClient创建Completion实例
     *
     * @param client OpenAI客户端
     * @return LLMCompletion实例
     */
    public static LLMCompletion createCompletion(OpenAILLMClient client) {
        if (client == null) {
            throw new OpenSagentLLMException("OpenAILLMClient不能为空");
        }
        return new OpenAICompletion(client.getOpenAIClient(), client.getConfig().getModel());
    }
}
