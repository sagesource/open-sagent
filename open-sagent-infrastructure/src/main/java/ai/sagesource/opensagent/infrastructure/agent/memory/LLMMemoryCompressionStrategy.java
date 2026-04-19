package ai.sagesource.opensagent.infrastructure.agent.memory;

import ai.sagesource.opensagent.core.agent.memory.MemoryCompressionStrategy;
import ai.sagesource.opensagent.core.agent.memory.MemoryItem;
import ai.sagesource.opensagent.core.agent.prompt.PromptRenderContext;
import ai.sagesource.opensagent.core.agent.prompt.PromptTemplate;
import ai.sagesource.opensagent.core.llm.completion.CompletionRequest;
import ai.sagesource.opensagent.core.llm.completion.CompletionResponse;
import ai.sagesource.opensagent.core.llm.completion.LLMCompletion;
import ai.sagesource.opensagent.core.llm.message.AssistantCompletionMessage;
import ai.sagesource.opensagent.core.llm.message.CompletionMessage;
import ai.sagesource.opensagent.core.llm.message.SystemCompletionMessage;
import ai.sagesource.opensagent.core.llm.message.UserCompletionMessage;
import ai.sagesource.opensagent.infrastructure.agent.prompt.DefaultPromptTemplate;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 基于 LLM 的智能记忆压缩策略
 * <p>
 * 通过注入 LLMCompletion，调用大模型对历史对话进行语义理解和信息提炼，生成高质量的记忆摘要。
 * <ul>
 *     <li>系统 Prompt 由外部传入的 {@code systemPromptTemplate} 提供，包含压缩指令与角色定义，无内置预设</li>
 *     <li>用户 Prompt 使用内置 {@code DEFAULT_USER_TEMPLATE}，仅包含 {@code {{memoryItems}}}、{@code {{messages}}} 动态内容占位符，不支持外部替换</li>
 * </ul>
 *
 * @author: sage.xue
 * @time: 2026/4/19
 */
@Slf4j
public class LLMMemoryCompressionStrategy implements MemoryCompressionStrategy {

    /**
     * 默认用户 Prompt 模板（内置预设，仅包含动态内容占位符）
     * <p>
     * 压缩指令由外部传入的 {@code systemPromptTemplate} 提供。
     */
    public static final String DEFAULT_USER_TEMPLATE =
            "{{memoryItems}}" +
            "{{messages}}";

    private final LLMCompletion completion;
    private final PromptTemplate systemPromptTemplate;
    private final PromptTemplate userPromptTemplate;

    /**
     * 创建压缩策略
     *
     * @param completion            LLMCompletion 实例
     * @param systemPromptTemplate  系统 Prompt 模板（由外部提供，无内置预设）
     */
    public LLMMemoryCompressionStrategy(LLMCompletion completion, PromptTemplate systemPromptTemplate) {
        this.completion = completion;
        this.systemPromptTemplate = systemPromptTemplate;
        this.userPromptTemplate = new DefaultPromptTemplate(DEFAULT_USER_TEMPLATE);
    }

    @Override
    public String compress(
            List<MemoryItem> memoryItems,
            List<CompletionMessage> uncompressedMessages,
            String lastMemoryItemId,
            String lastMessageId) {

        // 构建 PromptRenderContext
        Map<String, String> variables = new HashMap<>();

        StringBuilder memoryBuilder = new StringBuilder();
        if (memoryItems != null && !memoryItems.isEmpty()) {
            memoryBuilder.append("【已有记忆】\n");
            for (MemoryItem item : memoryItems) {
                memoryBuilder.append(item.getContent()).append("\n");
            }
            memoryBuilder.append("\n");
        }
        variables.put("memoryItems", memoryBuilder.toString());

        StringBuilder messageBuilder = new StringBuilder();
        messageBuilder.append("【待压缩对话】\n");
        for (CompletionMessage msg : uncompressedMessages) {
            messageBuilder.append("[").append(msg.getRole()).append("]: ")
                    .append(msg.getTextContent()).append("\n");
        }
        variables.put("messages", messageBuilder.toString());

        PromptRenderContext context = PromptRenderContext.of(variables);

        String systemPrompt = systemPromptTemplate.render(PromptRenderContext.empty());
        String userPrompt = userPromptTemplate.render(context);

        CompletionRequest request = CompletionRequest.builder()
                .messages(List.of(
                        SystemCompletionMessage.of(systemPrompt),
                        UserCompletionMessage.of(userPrompt)
                ))
                .temperature(0.3)
                .maxTokens(500)
                .build();

        log.info("> MemoryCompression | 调用 LLM 进行记忆压缩, messages: {} <", uncompressedMessages.size());

        CompletionResponse response = completion.complete(request);
        if (response == null || response.getMessage() == null) {
            throw new RuntimeException("LLM 压缩记忆失败：响应为空");
        }

        AssistantCompletionMessage assistantMsg = response.getMessage();
        String compressed = assistantMsg.getTextContent();
        if (compressed == null || compressed.isBlank()) {
            throw new RuntimeException("LLM 压缩记忆失败：返回内容为空");
        }

        log.info("> MemoryCompression | LLM 记忆压缩完成, 结果长度: {} <", compressed.length());
        return compressed.trim();
    }
}
