package ai.sagesource.opensagent.core.agent.prompt;

import ai.sagesource.opensagent.core.agent.exception.OpenSagentPromptException;

/**
 * Prompt模板加载器接口
 * <p>
 * 定义Prompt模板的加载行为，不同实现可从不同来源加载模板
 *
 * @author: sage.xue
 * @time: 2026/4/14
 */
public interface PromptTemplateLoader {

    /**
     * 加载指定名称的Prompt模板
     *
     * @param templateName 模板名称（如文件名为 system-prompt.txt 则传入 system-prompt.txt）
     * @return Prompt模板实例
     * @throws OpenSagentPromptException 模板不存在或加载失败时抛出
     */
    PromptTemplate load(String templateName);
}
