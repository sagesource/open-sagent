package ai.sagesource.opensagent.infrastructure.agent.prompt;

import ai.sagesource.opensagent.core.agent.prompt.PromptRenderContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DefaultPromptTemplate单元测试
 *
 * @author: sage.xue
 * @time: 2026/4/14
 */
class DefaultPromptTemplateTest {

    @Test
    @DisplayName("无占位符模板渲染 - 返回原内容")
    void testRenderNoPlaceholder() {
        DefaultPromptTemplate template = new DefaultPromptTemplate("Hello, World!");
        String result = template.render(PromptRenderContext.empty());

        assertEquals("Hello, World!", result);
    }

    @Test
    @DisplayName("空模板渲染 - 返回空字符串")
    void testRenderEmptyTemplate() {
        DefaultPromptTemplate template = new DefaultPromptTemplate("");
        String result = template.render(PromptRenderContext.empty());

        assertEquals("", result);
    }

    @Test
    @DisplayName("占位符替换 - 成功")
    void testRenderWithPlaceholder() {
        DefaultPromptTemplate template = new DefaultPromptTemplate(
                "You are {{role}}, your task is {{task}}."
        );

        Map<String, String> variables = new HashMap<>();
        variables.put("role", "an assistant");
        variables.put("task", "to help users");

        String result = template.render(PromptRenderContext.of(variables));

        assertEquals("You are an assistant, your task is to help users.", result);
    }

    @Test
    @DisplayName("带默认值的占位符替换 - 成功")
    void testRenderWithDefaultValue() {
        DefaultPromptTemplate template = new DefaultPromptTemplate(
                "You are {{role:assistant}}, your task is {{task}}."
        );

        Map<String, String> variables = new HashMap<>();
        variables.put("role", "an expert");

        String result = template.render(PromptRenderContext.of(variables));

        assertEquals("You are an expert, your task is {{task}}.", result);
    }

    @Test
    @DisplayName("未提供变量时保留占位符 - 成功")
    void testRenderKeepPlaceholderWhenMissing() {
        DefaultPromptTemplate template = new DefaultPromptTemplate(
                "You are {{role}}, your task is {{task}}."
        );

        Map<String, String> variables = new HashMap<>();
        variables.put("role", "an assistant");

        String result = template.render(PromptRenderContext.of(variables));

        assertEquals("You are an assistant, your task is {{task}}.", result);
    }

    @Test
    @DisplayName("null上下文渲染 - 返回原内容")
    void testRenderWithNullContext() {
        DefaultPromptTemplate template = new DefaultPromptTemplate("Hello, {{name}}!");
        String result = template.render(null);

        assertEquals("Hello, {{name}}!", result);
    }

    @Test
    @DisplayName("获取原始内容 - 成功")
    void testGetRawContent() {
        DefaultPromptTemplate template = new DefaultPromptTemplate("Raw content");

        assertEquals("Raw content", template.getRawContent());
    }
}
