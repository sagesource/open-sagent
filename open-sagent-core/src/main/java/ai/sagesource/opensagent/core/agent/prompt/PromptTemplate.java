package ai.sagesource.opensagent.core.agent.prompt;

/**
 * Prompt模板接口
 * <p>
 * 定义Prompt模板的基本行为，支持基于占位符的动态渲染
 *
 * @author: sage.xue
 * @time: 2026/4/14
 */
public interface PromptTemplate {

    /**
     * 渲染模板，将占位符替换为上下文中的实际值
     *
     * @param context 渲染上下文
     * @return 渲染后的Prompt内容
     */
    String render(PromptRenderContext context);

    /**
     * 获取模板的原始内容（未渲染）
     *
     * @return 原始模板内容
     */
    String getRawContent();
}
