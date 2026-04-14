# 方案：005-Agent模块-Prompt设计方案

## 1. 背景与目的

### 1.1 背景

Open Sagent是一个AI-Agent框架，Agent运行需要依赖System Prompt来定义其行为、角色和能力。为了提升Prompt的可维护性和复用性，需要一套支持模板化加载和动态占位符替换的Prompt机制。

根据架构设计，Prompt模块需要：
1. 支持从文件系统加载Prompt模板
2. 支持模板内的占位符动态替换
3. 在Core模块定义抽象，在Infrastructure模块提供具体实现

### 1.2 目的

1. 设计可扩展的Prompt模板抽象，支持占位符替换
2. 实现文件系统Prompt加载器，支持指定路径和工程目录双来源
3. 建立统一的Prompt模块异常处理机制
4. 为后续Agent模块提供System Prompt生成能力

## 2. 修改方案

### 2.1 文件变更列表

| 文件路径 | 变更类型 | 说明 |
|----------|----------|------|
| `open-sagent-core/src/main/java/ai/sagesource/opensagent/core/agent/prompt/PromptTemplate.java` | 新增 | Prompt模板接口 |
| `open-sagent-core/src/main/java/ai/sagesource/opensagent/core/agent/prompt/PromptRenderContext.java` | 新增 | Prompt渲染上下文 |
| `open-sagent-core/src/main/java/ai/sagesource/opensagent/core/agent/prompt/PromptTemplateLoader.java` | 新增 | Prompt模板加载器接口 |
| `open-sagent-core/src/main/java/ai/sagesource/opensagent/core/agent/prompt/OpenSagentPromptException.java` | 新增 | Prompt模块异常类 |
| `open-sagent-infrastructure/src/main/java/ai/sagesource/opensagent/infrastructure/agent/prompt/DefaultPromptTemplate.java` | 新增 | 默认Prompt模板实现 |
| `open-sagent-infrastructure/src/main/java/ai/sagesource/opensagent/infrastructure/agent/prompt/FileSystemPromptTemplateLoader.java` | 新增 | 文件系统Prompt加载器 |
| `open-sagent-infrastructure/src/main/java/ai/sagesource/opensagent/infrastructure/agent/prompt/ClasspathPromptTemplateLoader.java` | 新增 | Classpath资源目录Prompt加载器 |
| `open-sagent-core/src/test/java/ai/sagesource/opensagent/core/agent/prompt/PromptRenderContextTest.java` | 新增 | 渲染上下文单元测试 |
| `open-sagent-infrastructure/src/test/java/ai/sagesource/opensagent/infrastructure/agent/prompt/DefaultPromptTemplateTest.java` | 新增 | 默认模板实现单元测试 |
| `open-sagent-infrastructure/src/test/java/ai/sagesource/opensagent/infrastructure/agent/prompt/FileSystemPromptTemplateLoaderTest.java` | 新增 | 文件系统加载器单元测试 |
| `open-sagent-infrastructure/src/test/java/ai/sagesource/opensagent/infrastructure/agent/prompt/ClasspathPromptTemplateLoaderTest.java` | 新增 | Classpath加载器单元测试 |

### 2.2 详细变更内容

#### 文件 1: `open-sagent-core/src/main/java/ai/sagesource/opensagent/core/agent/prompt/PromptTemplate.java`

```java
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
```

#### 文件 2: `open-sagent-core/src/main/java/ai/sagesource/opensagent/core/agent/prompt/PromptRenderContext.java`

```java
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
```

#### 文件 3: `open-sagent-core/src/main/java/ai/sagesource/opensagent/core/agent/prompt/PromptTemplateLoader.java`

```java
package ai.sagesource.opensagent.core.agent.prompt;

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
     * @throws PromptException 模板不存在或加载失败时抛出
     */
    PromptTemplate load(String templateName);
}
```

#### 文件 4: `open-sagent-core/src/main/java/ai/sagesource/opensagent/core/agent/prompt/OpenSagentPromptException.java`

```java
package ai.sagesource.opensagent.core.agent.prompt;

import ai.sagesource.opensagent.base.exception.OpenSagentException;

/**
 * Prompt模块异常类
 *
 * @author: sage.xue
 * @time: 2026/4/14
 */
public class OpenSagentPromptException extends OpenSagentException {

    public OpenSagentPromptException(String message) {
        super(message);
    }

    public OpenSagentPromptException(String message, Throwable cause) {
        super(message, cause);
    }

    public OpenSagentPromptException(Throwable cause) {
        super(cause);
    }
}
```

#### 文件 5: `open-sagent-infrastructure/src/main/java/ai/sagesource/opensagent/infrastructure/agent/prompt/DefaultPromptTemplate.java`

```java
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
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\\\{\\{([^}:]+?)(?::([^}]*))?\\}\\}");

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
```

#### 文件 6: `open-sagent-infrastructure/src/main/java/ai/sagesource/opensagent/infrastructure/agent/prompt/FileSystemPromptTemplateLoader.java`

```java
package ai.sagesource.opensagent.infrastructure.agent.prompt;

import ai.sagesource.opensagent.core.agent.prompt.OpenSagentPromptException;
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
```

#### 文件 7: `open-sagent-infrastructure/src/main/java/ai/sagesource/opensagent/infrastructure/agent/prompt/ClasspathPromptTemplateLoader.java`

```java
package ai.sagesource.opensagent.infrastructure.agent.prompt;

import ai.sagesource.opensagent.core.agent.prompt.OpenSagentPromptException;
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
```

#### 文件 8: `open-sagent-core/src/test/java/ai/sagesource/opensagent/core/agent/prompt/PromptRenderContextTest.java`

```java
package ai.sagesource.opensagent.core.agent.prompt;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PromptRenderContext单元测试
 *
 * @author: sage.xue
 * @time: 2026/4/14
 */
class PromptRenderContextTest {

    @Test
    @DisplayName("基于Map创建上下文 - 成功")
    void testOfWithMap() {
        Map<String, String> variables = new HashMap<>();
        variables.put("name", "OpenSagent");

        PromptRenderContext context = PromptRenderContext.of(variables);

        assertNotNull(context);
        assertEquals("OpenSagent", context.getVariables().get("name"));
    }

    @Test
    @DisplayName("创建空上下文 - 成功")
    void testEmpty() {
        PromptRenderContext context = PromptRenderContext.empty();

        assertNotNull(context);
        assertNotNull(context.getVariables());
        assertTrue(context.getVariables().isEmpty());
    }

    @Test
    @DisplayName("基于null Map创建上下文 - 成功")
    void testOfWithNullMap() {
        PromptRenderContext context = PromptRenderContext.of(null);

        assertNotNull(context);
        assertNotNull(context.getVariables());
        assertTrue(context.getVariables().isEmpty());
    }
}
```

#### 文件 9: `open-sagent-infrastructure/src/test/java/ai/sagesource/opensagent/infrastructure/agent/prompt/DefaultPromptTemplateTest.java`

```java
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
```

#### 文件 10: `open-sagent-infrastructure/src/test/java/ai/sagesource/opensagent/infrastructure/agent/prompt/FileSystemPromptTemplateLoaderTest.java`

```java
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
```

#### 文件 11: `open-sagent-infrastructure/src/test/java/ai/sagesource/opensagent/infrastructure/agent/prompt/ClasspathPromptTemplateLoaderTest.java`

```java
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
```

## 3. 影响范围分析

### 3.1 模块依赖关系

```
open-sagent-base (基础定义)
    └── OpenSagentException (全局异常基类)

open-sagent-core (抽象定义)
    ├── 依赖 open-sagent-base
    ├── PromptTemplate (接口)
    ├── PromptRenderContext (渲染上下文)
    ├── PromptTemplateLoader (接口)
    └── OpenSagentPromptException (继承OpenSagentException)

open-sagent-infrastructure (具体实现)
    ├── 依赖 open-sagent-core
    ├── DefaultPromptTemplate (默认模板实现)
    ├── FileSystemPromptTemplateLoader (文件系统加载器)
    └── ClasspathPromptTemplateLoader (Classpath资源加载器)
```

### 3.2 影响范围

| 模块 | 影响说明 |
|------|----------|
| open-sagent-core | 新增Agent Prompt核心抽象，无破坏性变更 |
| open-sagent-infrastructure | 新增Prompt模板加载和渲染实现，无破坏性变更 |
| open-sagent-tools | 无直接影响 |
| open-sagent-web | 无直接影响 |
| open-sagent-cli | 无直接影响 |

### 3.3 加载器职责说明

| 加载器 | 加载来源 | 使用场景 |
|--------|----------|----------|
| `FileSystemPromptTemplateLoader` | 本地文件系统（指定路径 / 工程目录） | 运行时动态加载外部Prompt文件 |
| `ClasspathPromptTemplateLoader` | Classpath资源目录（如 `src/main/resources/prompts/`） | 工程内嵌Prompt模板，随Jar包分发 |

### 3.4 扩展性说明

1. **支持多种模板来源**：`PromptTemplateLoader` 为接口，已提供 `FileSystemPromptTemplateLoader` 和 `ClasspathPromptTemplateLoader`，后续可扩展 `DatabasePromptTemplateLoader` 等
2. **支持多种渲染引擎**：`PromptTemplate` 为接口，后续可扩展支持表达式引擎（如SpEL、Freemarker）的高级实现
3. **占位符格式可扩展**：当前默认实现使用 `{{key:defaultValue}}`，后续可通过不同的 `PromptTemplate` 实现支持其他模板语法

## 4. 测试计划

### 4.1 单元测试

| 测试类 | 测试内容 |
|--------|----------|
| `PromptRenderContextTest` | 上下文构建、空上下文、null Map处理 |
| `DefaultPromptTemplateTest` | 占位符替换、默认值处理、缺失变量保留、空模板、null上下文 |
| `FileSystemPromptTemplateLoaderTest` | 指定路径加载、工程目录加载、优先级验证、异常场景 |
| `ClasspathPromptTemplateLoaderTest` | 默认路径加载、自定义路径加载、异常场景 |

### 4.2 集成测试

本方案不涉及外部服务调用，无需额外集成测试。文件系统加载器的集成场景已在单元测试中通过 `@TempDir` 覆盖。

**测试资源文件准备**（用于 `ClasspathPromptTemplateLoaderTest`）：

| 资源文件路径 | 内容 |
|-------------|------|
| `open-sagent-infrastructure/src/test/resources/prompts/classpath-test-prompt.txt` | `Classpath default prompt.` |
| `open-sagent-infrastructure/src/test/resources/custom-prompts/custom-test-prompt.txt` | `Custom classpath prompt.` |

### 4.3 测试执行

```bash
mvn clean test -pl open-sagent-core,open-sagent-infrastructure
```

## 5. 评审记录

| 评审人 | 时间 | 结论 | 备注 |
|--------|------|------|------|
| User | 2026-04-14 | 需修改 | PromptException名称不符合编码规范，应改为OpenSagentPromptException |
| User | 2026-04-14 | 需修改 | 没有实现通过工程目录加载，默认工程目录应基于 user.dir |
| User | 2026-04-14 | 需修改 | 需要支持从 Classpath/资源目录 加载工程内的 Prompt 模板 |
| User | 2026-04-14 | 通过 | 评审通过，进入实施阶段 |
