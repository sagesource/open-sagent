package ai.sagesource.opensagent.core.llm.completion;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * Completion抽象接口
 * <p>
 * 定义与大模型进行对话补全的统一接口，支持同步、异步、流式四种调用模式
 *
 * @author: sage.xue
 * @time: 2026/4/14
 */
public interface LLMCompletion {

    /**
     * 同步调用
     *
     * @param request 补全请求
     * @return 补全响应
     */
    CompletionResponse complete(CompletionRequest request);

    /**
     * 异步调用
     *
     * @param request  补全请求
     * @param executor 执行器（由调用方传入）
     * @return 异步补全响应
     */
    CompletableFuture<CompletionResponse> completeAsync(CompletionRequest request, Executor executor);

    /**
     * 同步流式调用
     * <p>
     * 通过Consumer逐块接收流式数据，返回取消令牌用于中断
     *
     * @param request  补全请求
     * @param consumer 流式分片消费者
     * @return 取消令牌
     */
    CompletionCancelToken stream(CompletionRequest request, Consumer<StreamChunk> consumer);

    /**
     * 异步流式调用
     *
     * @param request  补全请求
     * @param consumer 流式分片消费者
     * @param executor 执行器（由调用方传入）
     * @return 取消令牌
     */
    CompletionCancelToken streamAsync(CompletionRequest request, Consumer<StreamChunk> consumer, Executor executor);
}
