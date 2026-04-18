package ai.sagesource.opensagent.infrastructure.agent;

import ai.sagesource.opensagent.core.agent.AgentConfig;
import ai.sagesource.opensagent.core.agent.AgentResponse;
import ai.sagesource.opensagent.core.agent.exception.OpenSagentAgentException;
import ai.sagesource.opensagent.core.agent.prompt.PromptRenderContext;
import ai.sagesource.opensagent.core.agent.prompt.PromptTemplate;
import ai.sagesource.opensagent.core.llm.completion.*;
import ai.sagesource.opensagent.core.llm.message.*;
import ai.sagesource.opensagent.infrastructure.agent.memory.SimpleMemory;
import ai.sagesource.opensagent.infrastructure.agent.prompt.DefaultPromptTemplate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SimpleAgent单元测试
 *
 * @author: sage.xue
 * @time: 2026/4/16
 */
class SimpleAgentTest {

    /**
     * 用于测试的Fake LLMCompletion实现
     */
    static class FakeLLMCompletion implements LLMCompletion {
        private final CompletionResponse response;

        FakeLLMCompletion(CompletionResponse response) {
            this.response = response;
        }

        @Override
        public CompletionResponse complete(CompletionRequest request) {
            return response;
        }

        @Override
        public CompletableFuture<CompletionResponse> completeAsync(CompletionRequest request, Executor executor) {
            return CompletableFuture.supplyAsync(() -> complete(request), executor);
        }

        @Override
        public CompletionCancelToken stream(CompletionRequest request, Consumer<StreamChunk> consumer) {
            consumer.accept(StreamChunk.builder()
                    .deltaText("流式回复")
                    .finished(true)
                    .finishReason("stop")
                    .build());
            return new CompletionCancelToken() {
                @Override
                public void cancel() {}
                @Override
                public boolean isCancelled() { return false; }
            };
        }

        @Override
        public CompletionCancelToken streamAsync(CompletionRequest request, Consumer<StreamChunk> consumer, Executor executor) {
            return CompletableFuture.supplyAsync(() -> stream(request, consumer), executor).join();
        }
    }

    @Test
    @DisplayName("同步调用 - 成功")
    void testChat() {
        CompletionResponse response = CompletionResponse.builder()
                .responseId("resp-001")
                .message(AssistantCompletionMessage.of("你好，有什么可以帮你的？"))
                .finishReason("stop")
                .build();

        SimpleAgent agent = new SimpleAgent(
                "TestAgent",
                null,
                null,
                null,
                new FakeLLMCompletion(response),
                null,
                AgentConfig.builder().build()
        );

        AgentResponse result = agent.chat(UserCompletionMessage.of("你好"));

        assertNotNull(result);
        assertEquals("resp-001", result.getResponseId());
        assertEquals("你好，有什么可以帮你的？", result.getMessage().getTextContent());
        assertEquals("stop", result.getFinishReason());
    }

    @Test
    @DisplayName("同步调用 - Completion为空抛出异常")
    void testChatWithoutCompletion() {
        SimpleAgent agent = new SimpleAgent(
                "TestAgent",
                null, null, null, null, null, null
        );

        OpenSagentAgentException exception = assertThrows(
                OpenSagentAgentException.class,
                () -> agent.chat(UserCompletionMessage.of("你好"))
        );
        assertTrue(exception.getMessage().contains("未配置"));
    }

    @Test
    @DisplayName("异步调用 - 成功")
    void testChatAsync() {
        CompletionResponse response = CompletionResponse.builder()
                .message(AssistantCompletionMessage.of("异步回复"))
                .build();

        SimpleAgent agent = new SimpleAgent(
                "TestAgent",
                null, null, null,
                new FakeLLMCompletion(response),
                null, null
        );

        Executor executor = Executors.newSingleThreadExecutor();
        CompletableFuture<AgentResponse> future = agent.chatAsync(UserCompletionMessage.of("你好"), executor);

        AgentResponse result = future.join();
        assertEquals("异步回复", result.getMessage().getTextContent());
    }

    @Test
    @DisplayName("带Memory调用 - 保存对话历史")
    void testChatWithMemory() {
        CompletionResponse response = CompletionResponse.builder()
                .message(AssistantCompletionMessage.of("记住了"))
                .build();

        SimpleMemory memory = new SimpleMemory(10);
        SimpleAgent agent = new SimpleAgent(
                "TestAgent",
                null, null, memory,
                new FakeLLMCompletion(response),
                null, null
        );

        agent.chat(UserCompletionMessage.of("我叫张三"));

        assertEquals(2, memory.getMessages().size());
        assertEquals(2, memory.getUncompressedMessages().size());
        assertEquals(MessageRole.ASSISTANT, memory.getMessages().get(1).getRole());
    }

    @Test
    @DisplayName("带PromptTemplate调用 - 组装System消息")
    void testChatWithPromptTemplate() {
        CompletionResponse response = CompletionResponse.builder()
                .message(AssistantCompletionMessage.of("收到"))
                .build();

        PromptTemplate template = new DefaultPromptTemplate("你是一个{{role}}");

        SimpleAgent agent = new SimpleAgent(
                "TestAgent",
                template,
                PromptRenderContext.of(new java.util.HashMap<>() {{ put("role", "助手"); }}),
                null,
                new FakeLLMCompletion(response),
                null, null
        );

        AgentResponse result = agent.chat(UserCompletionMessage.of("你好"));
        assertEquals("收到", result.getMessage().getTextContent());
    }

    @Test
    @DisplayName("Temperature参数传递 - 成功")
    void testTemperaturePropagation() {
        CompletionResponse response = CompletionResponse.builder()
                .message(AssistantCompletionMessage.of("ok"))
                .build();

        SimpleAgent agent = new SimpleAgent(
                "TestAgent",
                null, null, null,
                new FakeLLMCompletion(response),
                null,
                AgentConfig.builder().temperature(0.5).build()
        );

        AgentResponse result = agent.chat(UserCompletionMessage.of("test"));
        assertNotNull(result);
    }

    @Test
    @DisplayName("同步流式调用 - 透传成功")
    void testStream() {
        CompletionResponse response = CompletionResponse.builder()
                .message(AssistantCompletionMessage.of("流式"))
                .build();

        SimpleAgent agent = new SimpleAgent(
                "TestAgent",
                null, null, null,
                new FakeLLMCompletion(response),
                null, null
        );

        StringBuilder sb = new StringBuilder();
        CompletionCancelToken token = agent.stream(
                UserCompletionMessage.of("你好"),
                chunk -> {
                    if (chunk.getDeltaText() != null) {
                        sb.append(chunk.getDeltaText());
                    }
                }
        );

        assertNotNull(token);
        assertEquals("流式回复", sb.toString());
    }
}
