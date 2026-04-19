package ai.sagesource.opensagent.infrastructure.agent.memory;

import ai.sagesource.opensagent.core.agent.memory.MemoryItem;
import ai.sagesource.opensagent.core.agent.prompt.PromptRenderContext;
import ai.sagesource.opensagent.core.agent.prompt.PromptTemplate;
import ai.sagesource.opensagent.core.llm.completion.CompletionRequest;
import ai.sagesource.opensagent.core.llm.completion.CompletionResponse;
import ai.sagesource.opensagent.core.llm.completion.LLMCompletion;
import ai.sagesource.opensagent.core.llm.message.AssistantCompletionMessage;
import ai.sagesource.opensagent.core.llm.message.UserCompletionMessage;
import ai.sagesource.opensagent.infrastructure.agent.prompt.DefaultPromptTemplate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * LLMMemoryCompressionStrategy 单元测试
 *
 * @author: sage.xue
 * @time: 2026/4/19
 */
class LLMMemoryCompressionStrategyTest {

    @Test
    @DisplayName("内置用户模板 + 外部系统模板压缩 - 成功")
    void testCompressWithSystemTemplate() {
        PromptTemplate systemTemplate = new DefaultPromptTemplate("你是一个记忆压缩专家");

        LLMCompletion mockCompletion = new LLMCompletion() {
            @Override
            public CompletionResponse complete(CompletionRequest request) {
                assertNotNull(request);
                assertEquals(2, request.getMessages().size());
                // 系统 Prompt 由外部传入
                assertTrue(request.getMessages().get(0).getTextContent().contains("记忆压缩专家"));
                // 用户 Prompt 仅包含动态内容占位符渲染结果
                String userPrompt = request.getMessages().get(1).getTextContent();
                assertTrue(userPrompt.contains("【待压缩对话】"));
                assertFalse(userPrompt.contains("记忆压缩助手")); // 压缩指令在系统 Prompt 中，不在用户 Prompt

                return CompletionResponse.builder()
                        .message(AssistantCompletionMessage.of("用户询问了天气，助手回答今天是晴天。"))
                        .build();
            }

            @Override
            public java.util.concurrent.CompletableFuture<CompletionResponse> completeAsync(CompletionRequest request, java.util.concurrent.Executor executor) {
                return null;
            }

            @Override
            public ai.sagesource.opensagent.core.llm.completion.CompletionCancelToken stream(CompletionRequest request, java.util.function.Consumer<ai.sagesource.opensagent.core.llm.completion.StreamChunk> consumer) {
                return null;
            }

            @Override
            public ai.sagesource.opensagent.core.llm.completion.CompletionCancelToken streamAsync(CompletionRequest request, java.util.function.Consumer<ai.sagesource.opensagent.core.llm.completion.StreamChunk> consumer, java.util.concurrent.Executor executor) {
                return null;
            }
        };

        LLMMemoryCompressionStrategy strategy = new LLMMemoryCompressionStrategy(mockCompletion, systemTemplate);

        List<MemoryItem> memoryItems = new ArrayList<>();
        List<UserCompletionMessage> messages = new ArrayList<>();
        messages.add(UserCompletionMessage.of("今天天气怎么样？"));

        String result = strategy.compress(memoryItems, new ArrayList<>(messages), null, "msg-001");

        assertNotNull(result);
        assertEquals("用户询问了天气，助手回答今天是晴天。", result);
    }

    @Test
    @DisplayName("PromptTemplate 占位符渲染 - 正确替换")
    void testPromptTemplateRendering() {
        DefaultPromptTemplate template = new DefaultPromptTemplate("记忆：{{memoryItems}}，对话：{{messages}}");
        PromptRenderContext ctx = PromptRenderContext.builder()
                .variables(new java.util.HashMap<>() {{
                    put("memoryItems", "已有记忆");
                    put("messages", "待压缩对话");
                }})
                .build();

        String rendered = template.render(ctx);
        assertEquals("记忆：已有记忆，对话：待压缩对话", rendered);
    }

    @Test
    @DisplayName("LLM 响应为空 - 抛出异常")
    void testCompressWhenResponseEmpty() {
        LLMCompletion mockCompletion = new LLMCompletion() {
            @Override
            public CompletionResponse complete(CompletionRequest request) {
                return CompletionResponse.builder().build();
            }

            @Override
            public java.util.concurrent.CompletableFuture<CompletionResponse> completeAsync(CompletionRequest request, java.util.concurrent.Executor executor) {
                return null;
            }

            @Override
            public ai.sagesource.opensagent.core.llm.completion.CompletionCancelToken stream(CompletionRequest request, java.util.function.Consumer<ai.sagesource.opensagent.core.llm.completion.StreamChunk> consumer) {
                return null;
            }

            @Override
            public ai.sagesource.opensagent.core.llm.completion.CompletionCancelToken streamAsync(CompletionRequest request, java.util.function.Consumer<ai.sagesource.opensagent.core.llm.completion.StreamChunk> consumer, java.util.concurrent.Executor executor) {
                return null;
            }
        };
        PromptTemplate systemTemplate = new DefaultPromptTemplate("系统提示");

        LLMMemoryCompressionStrategy strategy = new LLMMemoryCompressionStrategy(mockCompletion, systemTemplate);
        assertThrows(RuntimeException.class, () ->
                strategy.compress(new ArrayList<>(), new ArrayList<>(), null, null));
    }
}
