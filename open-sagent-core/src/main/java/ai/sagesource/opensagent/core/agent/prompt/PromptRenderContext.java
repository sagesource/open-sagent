package ai.sagesource.opensagent.core.agent.prompt;

import lombok.Builder;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * Prompt渲染上下文
 * <p>
 * 用于传递模板占位符替换所需的变量集合
 *
 * @author: sage.xue
 * @time: 2026/4/14
 */
@Data
@Builder
public class PromptRenderContext {

    /**
     * 占位符变量映射
     */
    @Builder.Default
    private Map<String, String> variables = new HashMap<>();

    /**
     * 基于变量映射快速创建上下文
     *
     * @param variables 变量映射
     * @return 渲染上下文
     */
    public static PromptRenderContext of(Map<String, String> variables) {
        return PromptRenderContext.builder()
                .variables(variables != null ? variables : new HashMap<>())
                .build();
    }

    /**
     * 创建空上下文
     *
     * @return 空渲染上下文
     */
    public static PromptRenderContext empty() {
        return PromptRenderContext.builder().build();
    }
}
