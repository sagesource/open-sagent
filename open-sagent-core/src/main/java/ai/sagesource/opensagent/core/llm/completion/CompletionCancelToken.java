package ai.sagesource.opensagent.core.llm.completion;

/**
 * 取消令牌接口
 * <p>
 * 用于在流式或异步调用过程中请求中断
 *
 * @author: sage.xue
 * @time: 2026/4/14
 */
public interface CompletionCancelToken {

    /**
     * 请求取消当前调用
     */
    void cancel();

    /**
     * 是否已取消
     *
     * @return true表示已取消
     */
    boolean isCancelled();
}
