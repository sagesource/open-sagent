package ai.sagesource.opensagent.infrastructure.agent.prompt;

import ai.sagesource.opensagent.core.agent.prompt.PromptRenderContext;
import ai.sagesource.opensagent.core.agent.prompt.PromptTemplate;
import lombok.Getter;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 默认Prompt模板实现
 * <p>
 * 支持占位符替换，占位符格式：{{key:defaultValue}} 或 {{key}}
 *
 * @author: sage.xue
 * @time: 2026/4/14
 */
public class DefaultPromptTemplate implements PromptTemplate {

    /**
     * 占位符正则：匹配 {{key}} 或 {{key:defaultValue}}
     * 支持key和defaultValue中包含空格
     */
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{\\{([^}:]+?)(?::([^}]*))?\\}\\}");

    @Getter
    private final String rawContent;

    public DefaultPromptTemplate(String rawContent) {
        this.rawContent = rawContent != null ? rawContent : "";
    }

    @Override
    public String render(PromptRenderContext context) {
        if (rawContent.isEmpty()) {
            return "";
        }
        if (context == null || context.getVariables() == null || context.getVariables().isEmpty()) {
            return rawContent;
        }

        Matcher matcher = PLACEHOLDER_PATTERN.matcher(rawContent);
        StringBuffer result = new StringBuffer();

        while (matcher.find()) {
            String key = matcher.group(1).trim();
            String defaultValue = matcher.group(2);

            String replacement = context.getVariables().get(key);
            if (replacement == null) {
                // 未提供变量时，保留原占位符（包含默认值也保留，由调用方决定是否使用默认值）
                continue;
            }

            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);

        return result.toString();
    }
}
