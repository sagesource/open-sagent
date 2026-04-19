package ai.sagesource.opensagent.infrastructure.agent;

import ai.sagesource.opensagent.core.agent.AgentConfig;
import ai.sagesource.opensagent.core.agent.AgentResponse;
import ai.sagesource.opensagent.core.agent.exception.OpenSagentAgentException;
import ai.sagesource.opensagent.infrastructure.agent.memory.SimpleMemory;
import ai.sagesource.opensagent.core.llm.completion.*;
import ai.sagesource.opensagent.core.llm.message.*;
import ai.sagesource.opensagent.core.llm.tool.ToolCall;
import ai.sagesource.opensagent.core.llm.tool.ToolRegistry;
import ai.sagesource.opensagent.infrastructure.llm.tool.AnnotatedTool;
import ai.sagesource.opensagent.infrastructure.llm.tool.annotation.Tool;
import ai.sagesource.opensagent.infrastructure.llm.tool.annotation.ToolParam;
import ai.sagesource.opensagent.infrastructure.llm.tool.metadata.ToolMetadata;
import ai.sagesource.opensagent.infrastructure.llm.tool.parser.ToolMetadataParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ReActAgent单元测试
 *
 * @author: sage.xue
 * @time: 2026/4/19
 */
class ReActAgentTest {

    static class FakeLLMCompletion implements LLMCompletion {
        private final Queue<CompletionResponse> completeResponses;
        private final Queue<List<StreamChunk>> streamResponses;

        static FakeLLMCompletion forComplete(CompletionResponse... responses) {
            FakeLLMCompletion fake = new FakeLLMCompletion();
            fake.completeResponses.addAll(Arrays.asList(responses));
            return fake;
        }

        static FakeLLMCompletion forStream(List<StreamChunk>... streamChunks) {
            FakeLLMCompletion fake = new FakeLLMCompletion();
            fake.streamResponses.addAll(Arrays.asList(streamChunks));
            return fake;
        }

        private FakeLLMCompletion() {
            this.completeResponses = new LinkedList<>();
            this.streamResponses = new LinkedList<>();
        }

        @Override
        public CompletionResponse complete(CompletionRequest request) {
            return completeResponses.poll();
        }

        @Override
        public CompletableFuture<CompletionResponse> completeAsync(CompletionRequest request, Executor executor) {
            return CompletableFuture.supplyAsync(() -> complete(request), executor);
        }

        @Override
        public CompletionCancelToken stream(CompletionRequest request, Consumer<StreamChunk> consumer) {
            List<StreamChunk> chunks = streamResponses.poll();
            if (chunks != null) {
                for (StreamChunk chunk : chunks) {
                    consumer.accept(chunk);
                }
            }
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

    static class FinishToolService {
        @Tool(name = "react_finish_answer", description = "任务完成，返回答案")
        public String finish(@ToolParam(name = "answer") String answer) {
            return answer;
        }

        @Tool(name = "search", description = "搜索信息")
        public String search(@ToolParam(name = "query") String query) {
            return "搜索结果: " + query;
        }
    }

    private ToolRegistry createToolRegistry() {
        ToolRegistry registry = new ToolRegistry();
        FinishToolService service = new FinishToolService();
        List<ToolMetadata> metadatas = ToolMetadataParser.parse(FinishToolService.class);
        for (ToolMetadata metadata : metadatas) {
            registry.register(new AnnotatedTool(service, metadata));
        }
        return registry;
    }

    @Test
    @DisplayName("ReAct同步调用 - 一轮结束工具直接完成")
    void testChatWithFinishTool() {
        CompletionResponse response = CompletionResponse.builder()
                .responseId("resp-001")
                .message(AssistantCompletionMessage.builder()
                        .contents(new ArrayList<>(List.of(
                                TextContent.builder().text("调用结束工具").build())))
                        .toolCalls(new ArrayList<>(List.of(
                                ToolCall.builder()
                                        .id("call-1")
                                        .name("react_finish_answer")
                                        .arguments(Map.of("answer", "这是最终答案"))
                                        .build())))
                        .build())
                .finishReason("tool_calls")
                .build();

        ReActAgent agent = new ReActAgent(
                "ReActTest",
                null, null, null,
                FakeLLMCompletion.forComplete(response),
                createToolRegistry(),
                AgentConfig.builder()
                        .enableTools(true)
                        .maxIterations(5)
                        .build()
        );

        AgentResponse result = agent.chat(UserCompletionMessage.of("你好"));

        assertNotNull(result);
        assertEquals("这是最终答案", result.getMessage().getTextContent());
        assertEquals("stop", result.getFinishReason());
    }

    @Test
    @DisplayName("ReAct同步调用 - 多轮工具调用后完成")
    void testChatWithMultiTurnTools() {
        CompletionResponse turn1 = CompletionResponse.builder()
                .responseId("resp-001")
                .message(AssistantCompletionMessage.builder()
                        .contents(new ArrayList<>(List.of(
                                TextContent.builder().text("需要搜索").build())))
                        .toolCalls(new ArrayList<>(List.of(
                                ToolCall.builder()
                                        .id("call-1")
                                        .name("search")
                                        .arguments(Map.of("query", "天气"))
                                        .build())))
                        .build())
                .finishReason("tool_calls")
                .build();

        CompletionResponse turn2 = CompletionResponse.builder()
                .responseId("resp-002")
                .message(AssistantCompletionMessage.builder()
                        .contents(new ArrayList<>(List.of(
                                TextContent.builder().text("调用结束").build())))
                        .toolCalls(new ArrayList<>(List.of(
                                ToolCall.builder()
                                        .id("call-2")
                                        .name("react_finish_answer")
                                        .arguments(Map.of("answer", "搜索完成"))
                                        .build())))
                        .build())
                .finishReason("tool_calls")
                .build();

        SimpleMemory memory = new SimpleMemory(10);
        ReActAgent agent = new ReActAgent(
                "ReActTest",
                null, null, memory,
                FakeLLMCompletion.forComplete(turn1, turn2),
                createToolRegistry(),
                AgentConfig.builder()
                        .enableTools(true)
                        .maxIterations(5)
                        .build()
        );

        AgentResponse result = agent.chat(UserCompletionMessage.of("查天气"));

        assertNotNull(result);
        assertEquals("搜索完成", result.getMessage().getTextContent());
        assertTrue(memory.getMessages().size() >= 4);
    }

    @Test
    @DisplayName("ReAct同步调用 - 超过最大迭代次数抛出异常")
    void testChatExceedMaxIterations() {
        CompletionResponse turn = CompletionResponse.builder()
                .responseId("resp-001")
                .message(AssistantCompletionMessage.builder()
                        .contents(new ArrayList<>(List.of(
                                TextContent.builder().text("继续搜索").build())))
                        .toolCalls(new ArrayList<>(List.of(
                                ToolCall.builder()
                                        .id("call-1")
                                        .name("search")
                                        .arguments(Map.of("query", "测试"))
                                        .build())))
                        .build())
                .finishReason("tool_calls")
                .build();

        ReActAgent agent = new ReActAgent(
                "ReActTest",
                null, null, null,
                FakeLLMCompletion.forComplete(turn, turn, turn),
                createToolRegistry(),
                AgentConfig.builder()
                        .enableTools(true)
                        .maxIterations(2)
                        .build()
        );

        OpenSagentAgentException exception = assertThrows(
                OpenSagentAgentException.class,
                () -> agent.chat(UserCompletionMessage.of("测试"))
        );
        assertTrue(exception.getMessage().contains("超过最大迭代次数"));
    }

    @Test
    @DisplayName("ReAct同步调用 - 未配置maxIterations抛出异常")
    void testChatWithoutMaxIterations() {
        ReActAgent agent = new ReActAgent(
                "ReActTest",
                null, null, null,
                FakeLLMCompletion.forComplete(),
                null,
                AgentConfig.builder().build()
        );

        OpenSagentAgentException exception = assertThrows(
                OpenSagentAgentException.class,
                () -> agent.chat(UserCompletionMessage.of("测试"))
        );
        assertTrue(exception.getMessage().contains("maxIterations"));
    }

    @Test
    @DisplayName("ReAct同步调用 - 无工具调用直接返回")
    void testChatWithoutToolCalls() {
        CompletionResponse response = CompletionResponse.builder()
                .responseId("resp-001")
                .message(AssistantCompletionMessage.of("直接回复"))
                .finishReason("stop")
                .build();

        ReActAgent agent = new ReActAgent(
                "ReActTest",
                null, null, null,
                FakeLLMCompletion.forComplete(response),
                null,
                AgentConfig.builder()
                        .maxIterations(5)
                        .build()
        );

        AgentResponse result = agent.chat(UserCompletionMessage.of("你好"));

        assertNotNull(result);
        assertEquals("直接回复", result.getMessage().getTextContent());
    }

    @Test
    @DisplayName("ReAct流式调用 - 一轮流式后直接完成")
    void testStreamSingleRound() {
        List<StreamChunk> chunks = List.of(
                StreamChunk.builder().deltaText("这是").aggregatedText("这是").build(),
                StreamChunk.builder().deltaText("答案").aggregatedText("这是答案").build(),
                StreamChunk.builder().finished(true).finishReason("stop").aggregatedText("这是答案").build()
        );

        ReActAgent agent = new ReActAgent(
                "ReActTest",
                null, null, null,
                FakeLLMCompletion.forStream(chunks),
                null,
                AgentConfig.builder()
                        .maxIterations(5)
                        .build()
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
        assertEquals("这是答案", sb.toString());
    }

    @Test
    @DisplayName("ReAct流式调用 - 多轮流式后结束工具完成")
    void testStreamMultiTurnWithFinishTool() {
        List<StreamChunk> round1 = List.of(
                StreamChunk.builder().deltaText("思考中").aggregatedText("思考中").build(),
                StreamChunk.builder()
                        .deltaToolCalls(List.of(
                                ToolCall.builder().id("call-1").name("search").arguments(Map.of("query", "天气")).build()))
                        .aggregatedText("思考中")
                        .build(),
                StreamChunk.builder().finished(true).finishReason("tool_calls").aggregatedText("思考中").build()
        );

        List<StreamChunk> round2 = List.of(
                StreamChunk.builder().deltaText("完成").aggregatedText("完成").build(),
                StreamChunk.builder()
                        .deltaToolCalls(List.of(
                                ToolCall.builder().id("call-2").name("react_finish_answer").arguments(Map.of("answer", "晴天")).build()))
                        .aggregatedText("完成")
                        .build(),
                StreamChunk.builder().finished(true).finishReason("tool_calls").aggregatedText("完成").build()
        );

        SimpleMemory memory = new SimpleMemory(10);
        ReActAgent agent = new ReActAgent(
                "ReActTest",
                null, null, memory,
                FakeLLMCompletion.forStream(round1, round2),
                createToolRegistry(),
                AgentConfig.builder()
                        .enableTools(true)
                        .maxIterations(5)
                        .build()
        );

        StringBuilder sb = new StringBuilder();
        List<Boolean> finishedList = new ArrayList<>();
        CompletionCancelToken token = agent.stream(
                UserCompletionMessage.of("查天气"),
                chunk -> {
                    if (chunk.getDeltaText() != null) {
                        sb.append(chunk.getDeltaText());
                    }
                    finishedList.add(chunk.isFinished());
                }
        );

        assertNotNull(token);
        assertTrue(sb.toString().contains("思考中"));
        assertTrue(sb.toString().contains("完成") || sb.toString().contains("晴天"));
        assertTrue(memory.getMessages().size() >= 4);
    }

    @Test
    @DisplayName("ReAct流式调用 - 工具执行标记发送")
    void testStreamExecuteToolMarker() {
        List<StreamChunk> round1 = List.of(
                StreamChunk.builder().deltaText("思考").aggregatedText("思考").build(),
                StreamChunk.builder()
                        .deltaToolCalls(List.of(
                                ToolCall.builder().id("call-1").name("search").arguments(Map.of("query", "测试")).build()))
                        .aggregatedText("思考")
                        .build(),
                StreamChunk.builder().finished(true).finishReason("tool_calls").aggregatedText("思考").build()
        );

        List<StreamChunk> round2 = List.of(
                StreamChunk.builder().deltaText("结果").aggregatedText("结果").build(),
                StreamChunk.builder().finished(true).finishReason("stop").aggregatedText("结果").build()
        );

        ReActAgent agent = new ReActAgent(
                "ReActTest",
                null, null, null,
                FakeLLMCompletion.forStream(round1, round2),
                createToolRegistry(),
                AgentConfig.builder()
                        .enableTools(true)
                        .maxIterations(5)
                        .build()
        );

        List<String> deltaTexts = new ArrayList<>();
        CompletionCancelToken token = agent.stream(
                UserCompletionMessage.of("测试"),
                chunk -> {
                    if (chunk.getDeltaText() != null) {
                        deltaTexts.add(chunk.getDeltaText());
                    }
                }
        );

        assertNotNull(token);
        // 验证包含工具执行标记
        assertTrue(deltaTexts.contains("AGENT_ACTION[EXECUTE_TOOL]"));
    }

    @Test
    @DisplayName("ReAct流式调用 - 取消")
    void testStreamCancel() {
        List<StreamChunk> chunks = List.of(
                StreamChunk.builder().deltaText("开始").aggregatedText("开始").build(),
                StreamChunk.builder().deltaText("...").aggregatedText("开始...").build()
        );

        ReActAgent agent = new ReActAgent(
                "ReActTest",
                null, null, null,
                FakeLLMCompletion.forStream(chunks),
                null,
                AgentConfig.builder()
                        .maxIterations(5)
                        .build()
        );

        CompletionCancelToken token = agent.stream(
                UserCompletionMessage.of("测试"),
                chunk -> {}
        );

        token.cancel();
        assertTrue(token.isCancelled());
    }
}
