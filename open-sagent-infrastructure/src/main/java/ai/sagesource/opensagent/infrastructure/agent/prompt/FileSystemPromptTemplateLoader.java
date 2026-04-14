package ai.sagesource.opensagent.infrastructure.agent.prompt;

import ai.sagesource.opensagent.core.agent.exception.OpenSagentPromptException;
import ai.sagesource.opensagent.core.agent.prompt.PromptTemplate;
import ai.sagesource.opensagent.core.agent.prompt.PromptTemplateLoader;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 文件系统Prompt模板加载器
 * <p>
 * 支持从指定路径或工程目录的文件系统中加载Prompt模板。
 * 加载优先级：指定路径 > 工程目录
 *
 * @author: sage.xue
 * @time: 2026/4/14
 */
@Slf4j
public class FileSystemPromptTemplateLoader implements PromptTemplateLoader {

    /**
     * 指定路径（最高优先级）
     */
    private final Path explicitPath;

    /**
     * 工程目录路径（默认基于 user.dir 的 prompts/ 目录）
     */
    private final Path projectPath;

    /**
     * 构造加载器
     *
     * @param explicitPath 指定路径（可为null）
     * @param projectPath  工程目录路径（可为null，默认基于 user.dir 的 prompts/）
     */
    public FileSystemPromptTemplateLoader(Path explicitPath, Path projectPath) {
        this.explicitPath = explicitPath;
        this.projectPath = projectPath != null ? projectPath : Paths.get(System.getProperty("user.dir"), "prompts");
    }

    /**
     * 使用默认工程目录路径构造加载器
     */
    public FileSystemPromptTemplateLoader() {
        this(null, null);
    }

    /**
     * 使用指定路径构造加载器
     *
     * @param explicitPath 指定路径
     */
    public FileSystemPromptTemplateLoader(Path explicitPath) {
        this(explicitPath, null);
    }

    @Override
    public PromptTemplate load(String templateName) {
        if (templateName == null || templateName.isEmpty()) {
            throw new OpenSagentPromptException("模板名称不能为空");
        }

        Path templateFile = resolveTemplate(templateName);
        if (templateFile == null || !Files.exists(templateFile)) {
            throw new OpenSagentPromptException("Prompt模板不存在: " + templateName);
        }

        try {
            String content = Files.readString(templateFile, StandardCharsets.UTF_8);
            log.info("> Prompt | 加载模板成功: {} <", templateFile.toAbsolutePath());
            return new DefaultPromptTemplate(content);
        } catch (IOException e) {
            log.error("> Prompt | 读取模板失败: {} <", templateName, e);
            throw new OpenSagentPromptException("读取Prompt模板失败: " + templateName, e);
        }
    }

    /**
     * 解析模板文件路径
     *
     * @param templateName 模板名称
     * @return 模板文件路径（可能不存在）
     */
    private Path resolveTemplate(String templateName) {
        // 优先级1：指定路径
        if (explicitPath != null) {
            Path explicitTemplate = explicitPath.resolve(templateName);
            if (Files.exists(explicitTemplate)) {
                return explicitTemplate;
            }
        }

        // 优先级2：工程目录
        Path projectTemplate = projectPath.resolve(templateName);
        if (Files.exists(projectTemplate)) {
            return projectTemplate;
        }

        return null;
    }
}
