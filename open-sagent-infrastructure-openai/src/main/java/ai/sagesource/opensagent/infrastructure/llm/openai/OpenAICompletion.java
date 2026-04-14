package ai.sagesource.opensagent.infrastructure.llm.openai;

import ai.sagesource.opensagent.core.llm.completion.*;
import ai.sagesource.opensagent.core.llm.exception.OpenSagentLLMException;
import ai.sagesource.opensagent.core.llm.message.*;
import ai.sagesource.opensagent.core.llm.tool.ToolCall;
import ai.sagesource.opensagent.core.llm.tool.ToolDefinition;
import com.openai.client.OpenAIClient;
import com.openai.core.JsonValue;
import com.openai.helpers.ChatCompletionAccumulator;
import com.openai.models.FunctionDefinition;
import com.openai.models.FunctionParameters;
import com.openai.models.chat.completions.*;
import com.openai.models.completions.CompletionUsage;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * OpenAI Completion实现
 * <p>
 * 基于OpenAI SDK实现LLMCompletion接口
 *
 * @author: sage.xue
 * @time: 2026/4/14
 */
@Slf4j
public class OpenAICompletion implements LLMCompletion {

    private final OpenAIClient client;
    private final String model;

    public OpenAICompletion(OpenAIClient client, String model) {
        if (client == null) {
            throw new OpenSagentLLMException("OpenAIClient不能为空");
        }
        if (model == null || model.isEmpty()) {
            throw new OpenSagentLLMException("模型名称不能为空");
        }
        this.client = client;
        this.model = model;
    }

    @Override
    public CompletionResponse complete(CompletionRequest request) {
        try {
            log.info("> Completion | 开始同步调用，模型: {} <", model);
            ChatCompletionCreateParams params = buildParams(request);
            ChatCompletion completion = client.chat().completions().create(params);
            CompletionResponse response = mapResponse(completion);
            log.info("> Completion | 同步调用完成，finishReason: {} <", response.getFinishReason());
            return response;
        } catch (Exception e) {
            log.error("> Completion | 同步调用失败: {} <", e.getMessage(), e);
            throw new OpenSagentLLMException("OpenAI Completion调用失败: " + e.getMessage(), e);
        }
    }

    @Override
    public CompletableFuture<CompletionResponse> completeAsync(CompletionRequest request, Executor executor) {
        if (executor == null) {
            throw new OpenSagentLLMException("Executor不能为空");
        }
        return CompletableFuture.supplyAsync(() -> complete(request), executor);
    }

    @Override
    public CompletionCancelToken stream(CompletionRequest request, Consumer<StreamChunk> consumer) {
        AtomicBoolean cancelled = new AtomicBoolean(false);
        CompletionCancelToken token = new CompletionCancelToken() {
            @Override
            public void cancel() {
                cancelled.set(true);
            }

            @Override
            public boolean isCancelled() {
                return cancelled.get();
            }
        };

        try {
            log.info("> Completion | 开始流式调用，模型: {} <", model);
            ChatCompletionCreateParams params = buildParams(request);

            try (com.openai.core.http.StreamResponse<ChatCompletionChunk> streamResponse =
                         client.chat().completions().createStreaming(params)) {

                ChatCompletionAccumulator accumulator = ChatCompletionAccumulator.create();
                StringBuilder textBuilder = new StringBuilder();
                StringBuilder reasoningBuilder = new StringBuilder();
                String finishReason = null;

                Iterator<ChatCompletionChunk> iterator = streamResponse.stream().iterator();
                while (iterator.hasNext()) {
                    ChatCompletionChunk chunk = iterator.next();
                    if (cancelled.get()) {
                        log.warn("> Completion | 流式调用被取消 <");
                        break;
                    }

                    accumulator.accumulate(chunk);

                    ChatCompletionChunk.Choice choice = chunk.choices().isEmpty()
                            ? null : chunk.choices().get(0);
                    if (choice == null) {
                        continue;
                    }

                    ChatCompletionChunk.Choice.Delta delta = choice.delta();
                    if (choice.finishReason().isPresent()) {
                        finishReason = choice.finishReason().get().asString();
                    }

                    String deltaText = extractDeltaText(delta);
                    if (deltaText != null && !deltaText.isEmpty()) {
                        textBuilder.append(deltaText);
                    }

                    String deltaReasoning = extractDeltaReasoning(delta);
                    if (deltaReasoning != null && !deltaReasoning.isEmpty()) {
                        reasoningBuilder.append(deltaReasoning);
                    }

                    List<ToolCall> deltaToolCalls = extractDeltaToolCalls(accumulator, chunk);

                    StreamChunk streamChunk = StreamChunk.builder()
                            .deltaText(deltaText)
                            .deltaReasoningText(deltaReasoning)
                            .deltaToolCalls(deltaToolCalls)
                            .aggregatedText(textBuilder.toString())
                            .finishReason(finishReason)
                            .build();

                    if (streamChunk.hasDelta() || finishReason != null) {
                        consumer.accept(streamChunk);
                    }

                    if (finishReason != null) {
                        ChatCompletion accumulated = accumulator.chatCompletion();
                        CompletionResponse finalResponse = mapResponse(accumulated);
                        List<ToolCall> finalToolCalls = finalResponse.getMessage() != null
                                ? finalResponse.getMessage().getToolCalls() : new ArrayList<>();
                        if (!finalToolCalls.isEmpty()) {
                            consumer.accept(StreamChunk.builder()
                                    .deltaToolCalls(finalToolCalls)
                                    .aggregatedText(textBuilder.toString())
                                    .finishReason(finishReason)
                                    .build());
                        }
                        consumer.accept(StreamChunk.builder()
                                .finished(true)
                                .finishReason(finishReason)
                                .aggregatedText(textBuilder.toString())
                                .build());
                        break;
                    }
                }
            }

            log.info("> Completion | 流式调用结束 <");
        } catch (Exception e) {
            log.error("> Completion | 流式调用失败: {} <", e.getMessage(), e);
            throw new OpenSagentLLMException("OpenAI Completion流式调用失败: " + e.getMessage(), e);
        }

        return token;
    }

    @Override
    public CompletionCancelToken streamAsync(CompletionRequest request, Consumer<StreamChunk> consumer, Executor executor) {
        if (executor == null) {
            throw new OpenSagentLLMException("Executor不能为空");
        }
        AtomicBoolean cancelled = new AtomicBoolean(false);
        CompletionCancelToken token = new CompletionCancelToken() {
            @Override
            public void cancel() {
                cancelled.set(true);
            }

            @Override
            public boolean isCancelled() {
                return cancelled.get();
            }
        };

        CompletableFuture.runAsync(() -> stream(request, consumer), executor);
        return token;
    }

    // ========== 私有辅助方法 ==========

    private ChatCompletionCreateParams buildParams(CompletionRequest request) {
        ChatCompletionCreateParams.Builder builder = ChatCompletionCreateParams.builder()
                .model(model);

        if (request.getMessages() != null) {
            for (CompletionMessage msg : request.getMessages()) {
                builder.addMessage(convertMessage(msg));
            }
        }

        if (request.getTools() != null && !request.getTools().isEmpty()) {
            for (ToolDefinition tool : request.getTools()) {
                builder.addTool(convertTool(tool));
            }
        }

        if (request.getTemperature() != null) {
            builder.temperature(request.getTemperature());
        }
        if (request.getMaxTokens() != null) {
            builder.maxTokens(request.getMaxTokens().longValue());
        }

        return builder.build();
    }

    private ChatCompletionMessageParam convertMessage(CompletionMessage msg) {
        switch (msg.getRole()) {
            case SYSTEM -> {
                return ChatCompletionMessageParam.ofSystem(
                        ChatCompletionSystemMessageParam.builder()
                                .content(msg.getTextContent())
                                .build());
            }
            case USER -> {
                return ChatCompletionMessageParam.ofUser(
                        ChatCompletionUserMessageParam.builder()
                                .content(msg.getTextContent())
                                .build());
            }
            case ASSISTANT -> {
                ChatCompletionAssistantMessageParam.Builder assistantBuilder =
                        ChatCompletionAssistantMessageParam.builder()
                                .content(msg.getTextContent());
                return ChatCompletionMessageParam.ofAssistant(assistantBuilder.build());
            }
            case TOOL -> {
                if (msg instanceof ToolCompletionMessage toolMsg) {
                    return ChatCompletionMessageParam.ofTool(
                            ChatCompletionToolMessageParam.builder()
                                    .content(toolMsg.getTextContent())
                                    .toolCallId(toolMsg.getToolCallId())
                                    .build());
                }
                return ChatCompletionMessageParam.ofTool(
                        ChatCompletionToolMessageParam.builder()
                                .content(msg.getTextContent())
                                .build());
            }
            default -> {
                return ChatCompletionMessageParam.ofUser(
                        ChatCompletionUserMessageParam.builder()
                                .content(msg.getTextContent())
                                .build());
            }
        }
    }

    private ChatCompletionTool convertTool(ToolDefinition tool) {
        Map<String, JsonValue> paramProperties = tool.toParameterSchema().entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> JsonValue.from(e.getValue()),
                        (a, b) -> a,
                        LinkedHashMap::new
                ));

        FunctionParameters functionParameters = FunctionParameters.builder()
                .putAllAdditionalProperties(paramProperties)
                .build();

        FunctionDefinition functionDefinition = FunctionDefinition.builder()
                .name(tool.getName())
                .description(tool.getDescription())
                .parameters(functionParameters)
                .build();

        ChatCompletionFunctionTool functionTool = ChatCompletionFunctionTool.builder()
                .function(functionDefinition)
                .build();

        return ChatCompletionTool.ofFunction(functionTool);
    }

    private CompletionResponse mapResponse(ChatCompletion completion) {
        if (completion.choices().isEmpty()) {
            throw new OpenSagentLLMException("OpenAI返回空choices");
        }
        ChatCompletion.Choice choice = completion.choices().get(0);
        ChatCompletionMessage message = choice.message();

        AssistantCompletionMessage assistantMsg = AssistantCompletionMessage.builder()
                .messageId(completion.id())
                .contents(new ArrayList<>(List.of(
                        TextContent.builder().text(message.content().orElse("")).build())))
                .build();

        if (message.toolCalls().isPresent() && !message.toolCalls().get().isEmpty()) {
            List<ToolCall> toolCalls = new ArrayList<>();
            for (ChatCompletionMessageToolCall tc : message.toolCalls().get()) {
                if (tc.isFunction()) {
                    ChatCompletionMessageFunctionToolCall functionTc = tc.asFunction();
                    toolCalls.add(ToolCall.builder()
                            .id(functionTc.id())
                            .name(functionTc.function().name())
                            .arguments(parseArguments(functionTc.function().arguments()))
                            .build());
                }
            }
            assistantMsg.setToolCalls(toolCalls);
        }

        TokenUsage usage = null;
        if (completion.usage().isPresent()) {
            CompletionUsage u = completion.usage().get();
            usage = TokenUsage.builder()
                    .promptTokens((int) u.promptTokens())
                    .completionTokens((int) u.completionTokens())
                    .totalTokens((int) u.totalTokens())
                    .build();
        }

        return CompletionResponse.builder()
                .responseId(completion.id())
                .model(completion.model())
                .message(assistantMsg)
                .finishReason(choice.finishReason() != null ? choice.finishReason().asString() : null)
                .usage(usage)
                .build();
    }

    private String extractDeltaText(ChatCompletionChunk.Choice.Delta delta) {
        if (delta == null) {
            return null;
        }
        return delta.content().orElse(null);
    }

    private String extractDeltaReasoning(ChatCompletionChunk.Choice.Delta delta) {
        return null;
    }

    private List<ToolCall> extractDeltaToolCalls(ChatCompletionAccumulator accumulator, ChatCompletionChunk chunk) {
        List<ToolCall> result = new ArrayList<>();
        ChatCompletion accumulated = accumulator.chatCompletion();
        if (accumulated.choices().isEmpty()) {
            return result;
        }
        ChatCompletionMessage message = accumulated.choices().get(0).message();
        if (message.toolCalls().isEmpty()) {
            return result;
        }
        Set<Integer> indices = new HashSet<>();
        ChatCompletionChunk.Choice choice = chunk.choices().isEmpty() ? null : chunk.choices().get(0);
        if (choice != null && choice.delta() != null && choice.delta().toolCalls().isPresent()) {
            for (ChatCompletionChunk.Choice.Delta.ToolCall dt : choice.delta().toolCalls().get()) {
                indices.add((int) dt.index());
            }
        }
        List<ChatCompletionMessageToolCall> all = message.toolCalls().get();
        for (int index : indices) {
            if (index >= 0 && index < all.size()) {
                ChatCompletionMessageToolCall tc = all.get(index);
                if (tc.isFunction()) {
                    ChatCompletionMessageFunctionToolCall functionTc = tc.asFunction();
                    result.add(ToolCall.builder()
                            .id(functionTc.id())
                            .name(functionTc.function().name())
                            .arguments(parseArguments(functionTc.function().arguments()))
                            .build());
                }
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseArguments(String json) {
        if (json == null || json.isEmpty()) {
            return new LinkedHashMap<>();
        }
        try {
            com.alibaba.fastjson2.JSONObject obj = com.alibaba.fastjson2.JSON.parseObject(json);
            return obj;
        } catch (Exception e) {
            log.warn("> Completion | 解析Tool参数失败: {} <", json);
            return new LinkedHashMap<>();
        }
    }
}
