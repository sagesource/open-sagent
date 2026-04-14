package ai.sagesource.opensagent.infrastructure.agent.prompt;

import ai.sagesource.opensagent.core.agent.prompt.OpenSagentPromptException;
import ai.sagesource.opensagent.core.agent.prompt.PromptTemplate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * FileSystemPromptTemplateLoader单元测试
 *
 * @author: sage.xue
 * @time: 2026/4/14
 */
class FileSystemPromptTemplateLoaderTest {

    @Test
    @DisplayName("从指定路径加载模板 - 成功")
    void testLoadFromExplicitPath(@TempDir Path tempDir) throws IOException {
        Path explicitDir = tempDir.resolve("explicit");
        Files.createDirectories(explicitDir);
        Path templateFile = explicitDir.resolve("test-prompt.txt");
        Files.writeString(templateFile, "You are {{role}}.", StandardCharsets.UTF_8);

        FileSystemPromptTemplateLoader loader = new FileSystemPromptTemplateLoader(explicitDir);
        PromptTemplate template = loader.load("test-prompt.txt");

        assertNotNull(template);
        assertEquals("You are {{role}}.", template.getRawContent());
    }

    @Test
    @DisplayName("从工程目录加载模板 - 成功")
    void testLoadFromProjectPath(@TempDir Path tempDir) throws IOException {
        Path projectDir = tempDir.resolve("prompts");
        Files.createDirectories(projectDir);
        Path templateFile = projectDir.resolve("project-prompt.txt");
        Files.writeString(templateFile, "Project prompt.", StandardCharsets.UTF_8);

        FileSystemPromptTemplateLoader loader = new FileSystemPromptTemplateLoader(null, projectDir);
        PromptTemplate template = loader.load("project-prompt.txt");

        assertNotNull(template);
        assertEquals("Project prompt.", template.getRawContent());
    }

    @Test
    @DisplayName("指定路径优先级高于工程目录 - 成功")
    void testExplicitPathPriority(@TempDir Path tempDir) throws IOException {
        Path explicitDir = tempDir.resolve("explicit");
        Path projectDir = tempDir.resolve("prompts");
        Files.createDirectories(explicitDir);
        Files.createDirectories(projectDir);

        Files.writeString(explicitDir.resolve("priority.txt"), "Explicit version.", StandardCharsets.UTF_8);
        Files.writeString(projectDir.resolve("priority.txt"), "Project version.", StandardCharsets.UTF_8);

        FileSystemPromptTemplateLoader loader = new FileSystemPromptTemplateLoader(explicitDir, projectDir);
        PromptTemplate template = loader.load("priority.txt");

        assertEquals("Explicit version.", template.getRawContent());
    }

    @Test
    @DisplayName("模板不存在 - 抛出异常")
    void testLoadNonExistentTemplate(@TempDir Path tempDir) {
        FileSystemPromptTemplateLoader loader = new FileSystemPromptTemplateLoader(tempDir);

        OpenSagentPromptException exception = assertThrows(
                OpenSagentPromptException.class,
                () -> loader.load("not-found.txt"),
                "模板不存在时应抛出异常"
        );
        assertTrue(exception.getMessage().contains("不存在"));
    }

    @Test
    @DisplayName("模板名称为空 - 抛出异常")
    void testLoadEmptyTemplateName() {
        FileSystemPromptTemplateLoader loader = new FileSystemPromptTemplateLoader();

        OpenSagentPromptException exception = assertThrows(
                OpenSagentPromptException.class,
                () -> loader.load(""),
                "模板名称为空时应抛出异常"
        );
        assertTrue(exception.getMessage().contains("不能为空"));
    }
}
