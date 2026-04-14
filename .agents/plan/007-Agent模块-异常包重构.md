# 方案：007-Agent模块-异常包重构

## 1. 背景与目的

当前 `OpenSagentPromptException` 位于 `ai.sagesource.opensagent.core.agent.prompt` 包下，属于 Prompt 模块的异常类。随着 Agent 模块的扩展，异常类应统一收敛到 `agent.exception` 包下，以符合项目异常体系规范（参考 `project-exception-system` SKILL），提升模块结构的清晰度和可维护性。

## 2. 修改方案

### 2.1 文件变更列表

| 文件路径 | 变更类型 | 说明 |
|----------|----------|------|
| `open-sagent-core/src/main/java/ai/sagesource/opensagent/core/agent/exception/OpenSagentPromptException.java` | 新增 | 异常类新包位置 |
| `open-sagent-core/src/main/java/ai/sagesource/opensagent/core/agent/prompt/OpenSagentPromptException.java` | 删除 | 异常类旧包位置 |
| `open-sagent-core/src/main/java/ai/sagesource/opensagent/core/agent/prompt/PromptTemplateLoader.java` | 修改 | 更新 Javadoc 中的异常引用（import 路径） |
| `open-sagent-infrastructure/src/main/java/ai/sagesource/opensagent/infrastructure/agent/prompt/FileSystemPromptTemplateLoader.java` | 修改 | 更新 import 路径 |
| `open-sagent-infrastructure/src/main/java/ai/sagesource/opensagent/infrastructure/agent/prompt/ClasspathPromptTemplateLoader.java` | 修改 | 更新 import 路径 |
| `open-sagent-infrastructure/src/test/java/ai/sagesource/opensagent/infrastructure/agent/prompt/ClasspathPromptTemplateLoaderTest.java` | 修改 | 更新 import 路径 |
| `open-sagent-infrastructure/src/test/java/ai/sagesource/opensagent/infrastructure/agent/prompt/FileSystemPromptTemplateLoaderTest.java` | 修改 | 更新 import 路径 |

### 2.2 详细变更内容

#### 文件 1: `open-sagent-core/src/main/java/ai/sagesource/opensagent/core/agent/exception/OpenSagentPromptException.java`

```java
package ai.sagesource.opensagent.core.agent.exception;

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

#### 文件 2: `open-sagent-core/src/main/java/ai/sagesource/opensagent/core/agent/prompt/PromptTemplateLoader.java`

```java
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
```

#### 文件 3: `open-sagent-infrastructure/src/main/java/ai/sagesource/opensagent/infrastructure/agent/prompt/FileSystemPromptTemplateLoader.java`

- 将 `import ai.sagesource.opensagent.core.agent.prompt.OpenSagentPromptException;` 修改为：
  `import ai.sagesource.opensagent.core.agent.exception.OpenSagentPromptException;`

#### 文件 4: `open-sagent-infrastructure/src/main/java/ai/sagesource/opensagent/infrastructure/agent/prompt/ClasspathPromptTemplateLoader.java`

- 将 `import ai.sagesource.opensagent.core.agent.prompt.OpenSagentPromptException;` 修改为：
  `import ai.sagesource.opensagent.core.agent.exception.OpenSagentPromptException;`

#### 文件 5: `open-sagent-infrastructure/src/test/java/ai/sagesource/opensagent/infrastructure/agent/prompt/ClasspathPromptTemplateLoaderTest.java`

- 将 `import ai.sagesource.opensagent.core.agent.prompt.OpenSagentPromptException;` 修改为：
  `import ai.sagesource.opensagent.core.agent.exception.OpenSagentPromptException;`

#### 文件 6: `open-sagent-infrastructure/src/test/java/ai/sagesource/opensagent/infrastructure/agent/prompt/FileSystemPromptTemplateLoaderTest.java`

- 将 `import ai.sagesource.opensagent.core.agent.prompt.OpenSagentPromptException;` 修改为：
  `import ai.sagesource.opensagent.core.agent.exception.OpenSagentPromptException;`

## 3. 影响范围分析

- **内部影响**：仅涉及 `OpenSagentPromptException` 的包路径变更，异常类的继承关系、构造方法、行为均保持不变。
- **外部影响**：该异常类目前仅在 `open-sagent-core` 和 `open-sagent-infrastructure` 模块内部使用，无外部模块依赖。所有引用点已识别并在方案中覆盖。
- **兼容性**：纯包路径移动，API 签名不变，对业务逻辑无影响。

## 4. 测试计划

1. 执行 `mvn clean compile` 验证编译通过。
2. 执行 `mvn test -pl open-sagent-infrastructure` 验证 PromptTemplateLoader 相关测试通过。

## 5. 评审记录

| 评审人 | 时间 | 结论 | 备注 |
|--------|------|------|------|
| User | 2026-04-14 | 通过 | |
