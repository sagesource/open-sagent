package ai.sagesource.opensagent.core.agent;

import ai.sagesource.opensagent.core.llm.completion.CompletionCancelToken;
import ai.sagesource.opensagent.core.llm.completion.StreamChunk;
import ai.sagesource.opensagent.core.llm.message.UserCompletionMessage;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * Agent抽象接口
 * <p>
 * 定义Agent与大模型交互的统一接口，支持同步、异步、流式四种调用模式。
 * Agent负责整合Prompt、Memory、Tool和Completion能力，为调用方提供端到端的智能体服务。
 *
 * @author: sage.xue
 * @time: 2026/4/16
 */
public interface Agent {

    /**
     * 同步调用
     *
     * @param message 用户消息
     * @return Agent响应结果
     */
    default AgentResponse chat(UserCompletionMessage message) {
        return chat(List.of(message));
    }

    /**
     * 同步调用
     *
     * @param messages 用户消息列表
     * @return Agent响应结果
     */
    AgentResponse chat(List<UserCompletionMessage> messages);

    /**
     * 异步调用
     *
     * @param message  用户消息
     * @param executor 执行器
     * @return 异步Agent响应结果
     */
    default CompletableFuture<AgentResponse> chatAsync(UserCompletionMessage message, Executor executor) {
        return chatAsync(List.of(message), executor);
    }

    /**
     * 异步调用
     *
     * @param messages 用户消息列表
     * @param executor 执行器
     * @return 异步Agent响应结果
     */
    CompletableFuture<AgentResponse> chatAsync(List<UserCompletionMessage> messages, Executor executor);

    /**
     * 同步流式调用
     *
     * @param message  用户消息
     * @param consumer 流式分片消费者
     * @return 取消令牌
     */
    default CompletionCancelToken stream(UserCompletionMessage message, Consumer<StreamChunk> consumer) {
        return stream(List.of(message), consumer);
    }

    /**
     * 同步流式调用
     *
     * @param messages 用户消息列表
     * @param consumer 流式分片消费者
     * @return 取消令牌
     */
    CompletionCancelToken stream(List<UserCompletionMessage> messages, Consumer<StreamChunk> consumer);

    /**
     * 异步流式调用
     *
     * @param message  用户消息
     * @param consumer 流式分片消费者
     * @param executor 执行器
     * @return 取消令牌
     */
    default CompletionCancelToken streamAsync(UserCompletionMessage message, Consumer<StreamChunk> consumer, Executor executor) {
        return streamAsync(List.of(message), consumer, executor);
    }

    /**
     * 异步流式调用
     *
     * @param messages 用户消息列表
     * @param consumer 流式分片消费者
     * @param executor 执行器
     * @return 取消令牌
     */
    CompletionCancelToken streamAsync(List<UserCompletionMessage> messages, Consumer<StreamChunk> consumer, Executor executor);
}
