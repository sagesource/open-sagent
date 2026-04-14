package ai.sagesource.opensagent.infrastructure.agent.prompt;

import ai.sagesource.opensagent.core.agent.prompt.OpenSagentPromptException;
import ai.sagesource.opensagent.core.agent.prompt.PromptTemplate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ClasspathPromptTemplateLoader单元测试
 *
 * @author: sage.xue
 * @time: 2026/4/14
 */
class ClasspathPromptTemplateLoaderTest {

    @Test
    @DisplayName("从Classpath默认路径加载模板 - 成功")
    void testLoadFromDefaultClasspath() {
        // 预先在测试资源目录中放置模板：src/test/resources/prompts/classpath-test-prompt.txt
        ClasspathPromptTemplateLoader loader = new ClasspathPromptTemplateLoader();
        PromptTemplate template = loader.load("classpath-test-prompt.txt");

        assertNotNull(template);
        assertEquals("Classpath default prompt.", template.getRawContent().trim());
    }

    @Test
    @DisplayName("从自定义Classpath路径加载模板 - 成功")
    void testLoadFromCustomClasspath() {
        // 预先在测试资源目录中放置模板：src/test/resources/custom-prompts/custom-test-prompt.txt
        ClasspathPromptTemplateLoader loader = new ClasspathPromptTemplateLoader("custom-prompts/");
        PromptTemplate template = loader.load("custom-test-prompt.txt");

        assertNotNull(template);
        assertEquals("Custom classpath prompt.", template.getRawContent().trim());
    }

    @Test
    @DisplayName("Classpath模板不存在 - 抛出异常")
    void testLoadNonExistentClasspathTemplate() {
        ClasspathPromptTemplateLoader loader = new ClasspathPromptTemplateLoader();

        OpenSagentPromptException exception = assertThrows(
                OpenSagentPromptException.class,
                () -> loader.load("non-existent-template.txt"),
                "Classpath模板不存在时应抛出异常"
        );
        assertTrue(exception.getMessage().contains("不存在"));
    }

    @Test
    @DisplayName("模板名称为空 - 抛出异常")
    void testLoadEmptyTemplateName() {
        ClasspathPromptTemplateLoader loader = new ClasspathPromptTemplateLoader();

        OpenSagentPromptException exception = assertThrows(
                OpenSagentPromptException.class,
                () -> loader.load(""),
                "模板名称为空时应抛出异常"
        );
        assertTrue(exception.getMessage().contains("不能为空"));
    }
}
