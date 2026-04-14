package ai.sagesource.opensagent.infrastructure.agent.prompt;

import ai.sagesource.opensagent.core.agent.exception.OpenSagentPromptException;
import ai.sagesource.opensagent.core.agent.prompt.PromptTemplate;
import ai.sagesource.opensagent.core.agent.prompt.PromptTemplateLoader;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

/**
 * Classpath资源目录Prompt模板加载器
 * <p>
 * 支持从Classpath资源目录（如 src/main/resources/prompts/）加载Prompt模板。
 *
 * @author: sage.xue
 * @time: 2026/4/14
 */
@Slf4j
public class ClasspathPromptTemplateLoader implements PromptTemplateLoader {

    /**
     * 资源基础路径（默认为 prompts/）
     */
    private final String basePath;

    /**
     * 类加载器
     */
    private final ClassLoader classLoader;

    /**
     * 构造加载器，使用默认 prompts/ 基础路径
     */
    public ClasspathPromptTemplateLoader() {
        this("prompts/", Thread.currentThread().getContextClassLoader());
    }

    /**
     * 构造加载器，使用自定义基础路径
     *
     * @param basePath 资源基础路径（如 prompts/）
     */
    public ClasspathPromptTemplateLoader(String basePath) {
        this(basePath, Thread.currentThread().getContextClassLoader());
    }

    /**
     * 构造加载器
     *
     * @param basePath    资源基础路径
     * @param classLoader 类加载器
     */
    public ClasspathPromptTemplateLoader(String basePath, ClassLoader classLoader) {
        this.basePath = normalizeBasePath(basePath);
        this.classLoader = classLoader != null ? classLoader : Thread.currentThread().getContextClassLoader();
    }

    @Override
    public PromptTemplate load(String templateName) {
        if (templateName == null || templateName.isEmpty()) {
            throw new OpenSagentPromptException("模板名称不能为空");
        }

        String resourcePath = basePath + templateName;
        InputStream inputStream = classLoader.getResourceAsStream(resourcePath);

        if (inputStream == null) {
            log.warn("> Prompt | Classpath模板不存在: {} <", resourcePath);
            throw new OpenSagentPromptException("Classpath Prompt模板不存在: " + templateName);
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String content = reader.lines().collect(Collectors.joining("\n"));
            log.info("> Prompt | 加载Classpath模板成功: {} <", resourcePath);
            return new DefaultPromptTemplate(content);
        } catch (IOException e) {
            log.error("> Prompt | 读取Classpath模板失败: {} <", templateName, e);
            throw new OpenSagentPromptException("读取Classpath Prompt模板失败: " + templateName, e);
        }
    }

    /**
     * 规范化基础路径，确保以 / 结尾
     *
     * @param basePath 原始基础路径
     * @return 规范化后的路径
     */
    private String normalizeBasePath(String basePath) {
        if (basePath == null || basePath.isEmpty()) {
            return "";
        }
        return basePath.endsWith("/") ? basePath : basePath + "/";
    }
}
