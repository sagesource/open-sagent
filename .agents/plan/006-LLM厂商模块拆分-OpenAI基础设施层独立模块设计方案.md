# 方案：006-LLM厂商模块拆分-OpenAI基础设施层独立模块设计方案

## 1. 背景与目的

### 1.1 背景

根据项目最新架构设计 `project_system_infrastructure.md`，LLM厂商的具体实现应当位于独立的**厂商基础设施层**模块中：

```
open-sagent/
├── open-sagent-infrastructure              基础设施层：针对Core模块的抽象提供通用具体实现
├── open-sagent-infrastructure-openai       厂商基础设施层：针对OpenAI的LLM交互具体实现
```

当前项目中，OpenAI的LLM Client和Completion实现直接放在 `open-sagent-infrastructure` 模块下的 `llm/openai` 包中，不符合最新的工程架构说明。

### 1.2 目的

1. 按照最新架构设计，将OpenAI厂商的LLM实现从 `open-sagent-infrastructure` 拆分至独立的 `open-sagent-infrastructure-openai` 模块
2. `open-sagent-infrastructure` 仅保留通用基础设施能力（如Tool注解解析、AnnotatedTool等）
3. 为未来接入更多LLM厂商（Anthropic、Kimi、Qwen等）建立清晰的模块边界和扩展范式
4. 兼容OpenAI协议的第三方厂商（DeepSeek、SiliconFlow、Azure等）继续复用 `open-sagent-infrastructure-openai` 模块中的实现

## 2. 修改方案

### 2.1 文件变更列表

#### 新增模块：open-sagent-infrastructure-openai

| 文件路径 | 变更类型 | 说明 |
|----------|----------|------|
| `open-sagent-infrastructure-openai/pom.xml` | 新增 | 新模块的Maven POM文件 |
| `open-sagent-infrastructure-openai/src/main/java/.../infrastructure/llm/openai/OpenAILLMClient.java` | 新增（迁移） | 从 infrastructure 模块迁移 |
| `open-sagent-infrastructure-openai/src/main/java/.../infrastructure/llm/openai/OpenAILLMClientFactory.java` | 新增（迁移） | 从 infrastructure 模块迁移 |
| `open-sagent-infrastructure-openai/src/main/java/.../infrastructure/llm/openai/OpenAICompletion.java` | 新增（迁移） | 从 infrastructure 模块迁移 |
| `open-sagent-infrastructure-openai/src/main/java/.../infrastructure/llm/openai/OpenAICompletionFactory.java` | 新增（迁移） | 从 infrastructure 模块迁移 |
| `open-sagent-infrastructure-openai/src/test/java/.../infrastructure/llm/openai/OpenAILLMClientFactoryTest.java` | 新增（迁移） | 从 infrastructure 模块迁移 |
| `open-sagent-infrastructure-openai/src/test/java/.../infrastructure/llm/openai/OpenAILLMClientIntegrationTest.java` | 新增（迁移） | 从 infrastructure 模块迁移 |
| `open-sagent-infrastructure-openai/src/test/java/.../infrastructure/llm/openai/OpenAICompletionFactoryTest.java` | 新增（迁移） | 从 infrastructure 模块迁移 |
| `open-sagent-infrastructure-openai/src/test/java/.../infrastructure/llm/openai/OpenAICompletionIntegrationTest.java` | 新增（迁移） | 从 infrastructure 模块迁移 |

#### 修改模块：open-sagent-infrastructure

| 文件路径 | 变更类型 | 说明 |
|----------|----------|------|
| `open-sagent-infrastructure/pom.xml` | 修改 | 移除 `openai-java` SDK依赖 |
| `open-sagent-infrastructure/src/main/java/.../infrastructure/llm/openai/*.java` | 删除 | 所有OpenAI相关实现类迁移至新模块 |
| `open-sagent-infrastructure/src/test/java/.../infrastructure/llm/openai/*.java` | 删除 | 所有OpenAI相关测试类迁移至新模块 |

#### 修改模块：open-sagent-example

| 文件路径 | 变更类型 | 说明 |
|----------|----------|------|
| `open-sagent-example/pom.xml` | 修改 | 新增对 `open-sagent-infrastructure-openai` 的依赖 |
| `open-sagent-example/src/main/java/.../example/llm/CompatibleOpenAICompletionExample.java` | 修改 | 更新 import 路径（如需要） |

#### 修改模块：根POM

| 文件路径 | 变更类型 | 说明 |
|----------|----------|------|
| `pom.xml` | 修改 | 在 `<modules>` 中新增 `open-sagent-infrastructure-openai` |

### 2.2 详细变更内容

#### 文件 1: `open-sagent-infrastructure-openai/pom.xml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>ai.sagesource</groupId>
        <artifactId>open-sagent</artifactId>
        <version>${revision}</version>
    </parent>

    <artifactId>open-sagent-infrastructure-openai</artifactId>
    <name>Open Sagent Infrastructure - OpenAI</name>
    <description>厂商基础设施层：针对OpenAI及其兼容接口的LLM交互具体实现</description>

    <dependencies>
        <!-- Core Module -->
        <dependency>
            <groupId>ai.sagesource</groupId>
            <artifactId>open-sagent-core</artifactId>
            <version>${revision}</version>
        </dependency>

        <!-- OpenAI Java SDK -->
        <dependency>
            <groupId>com.openai</groupId>
            <artifactId>openai-java</artifactId>
        </dependency>

        <!-- Lombok -->
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <scope>provided</scope>
        </dependency>

        <!-- SLF4J -->
        <dependency>
            <groupId>org.slf4j</groupId>
            <artifactId>slf4j-api</artifactId>
        </dependency>

        <!-- Base Module (for DotEnvUtils in tests) -->
        <dependency>
            <groupId>ai.sagesource</groupId>
            <artifactId>open-sagent-base</artifactId>
            <version>${revision}</version>
            <scope>test</scope>
        </dependency>

        <!-- Test -->
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

</project>
```

#### 文件 2: `open-sagent-infrastructure/pom.xml`（修改后）

移除 `openai-java` 依赖，保留其他依赖不变：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>ai.sagesource</groupId>
        <artifactId>open-sagent</artifactId>
        <version>${revision}</version>
    </parent>

    <artifactId>open-sagent-infrastructure</artifactId>
    <name>Open Sagent Infrastructure</name>
    <description>基础设施层：针对Core模块的抽象提供具体实现</description>

    <dependencies>
        <!-- Core Module -->
        <dependency>
            <groupId>ai.sagesource</groupId>
            <artifactId>open-sagent-core</artifactId>
            <version>${revision}</version>
        </dependency>

        <!-- Lombok -->
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <scope>provided</scope>
        </dependency>

        <!-- SLF4J -->
        <dependency>
            <groupId>org.slf4j</groupId>
            <artifactId>slf4j-api</artifactId>
        </dependency>

        <!-- Spring Context (for @Configuration, etc.) -->
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-context</artifactId>
        </dependency>

        <!-- Test -->
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

</project>
```

#### 文件 3: `open-sagent-example/pom.xml`（修改后）

在 dependencies 中新增：

```xml
        <!-- OpenAI Infrastructure Module -->
        <dependency>
            <groupId>ai.sagesource</groupId>
            <artifactId>open-sagent-infrastructure-openai</artifactId>
            <version>${revision}</version>
        </dependency>
```

保留原有的 `open-sagent-infrastructure` 依赖（示例中可能需要使用Tool注解解析等通用基础设施）。

#### 文件 4: `pom.xml`（修改后 `<modules>` 部分）

```xml
    <modules>
        <module>open-sagent-base</module>
        <module>open-sagent-core</module>
        <module>open-sagent-infrastructure</module>
        <module>open-sagent-infrastructure-openai</module>
        <module>open-sagent-tools</module>
        <module>open-sagent-web</module>
        <module>open-sagent-cli</module>
        <module>open-sagent-example</module>
    </modules>
```

#### 文件 5: OpenAI 实现类（迁移说明）

以下文件从 `open-sagent-infrastructure` 原样迁移至 `open-sagent-infrastructure-openai`，**包路径保持不变**：

- `ai.sagesource.opensagent.infrastructure.llm.openai.OpenAILLMClient`
- `ai.sagesource.opensagent.infrastructure.llm.openai.OpenAILLMClientFactory`
- `ai.sagesource.opensagent.infrastructure.llm.openai.OpenAICompletion`
- `ai.sagesource.opensagent.infrastructure.llm.openai.OpenAICompletionFactory`

**不修改类内容**，仅移动文件位置。保持包路径不变是为了：
1. 最小化破坏性变更
2. 已有的示例代码和外部引用无需修改 import 语句
3. 兼容OpenAI协议的第三方调用示例继续正常工作

对应的测试文件同样原样迁移：

- `OpenAILLMClientFactoryTest.java`
- `OpenAILLMClientIntegrationTest.java`
- `OpenAICompletionFactoryTest.java`
- `OpenAICompletionIntegrationTest.java`

## 3. 影响范围分析

### 3.1 模块依赖关系（重构后）

```
open-sagent-base
    └── OpenSagentException / DotEnvUtils

open-sagent-core
    ├── LLMClient / LLMClientConfig
    ├── LLMCompletion / CompletionRequest / CompletionResponse
    ├── CompletionMessage / MessageRole / MessageContent
    ├── ToolDefinition / ToolCall / ToolResult
    └── Tool / ToolRegistry / ToolExecutor / AnnotatedTool (接口与执行框架)

open-sagent-infrastructure
    ├── 依赖 open-sagent-core
    ├── ToolMetadataParser (注解解析)
    ├── ToolMetadata / ToolParameterMetadata
    ├── @Tool / @ToolParam
    └── AnnotatedTool (反射执行实现)

open-sagent-infrastructure-openai
    ├── 依赖 open-sagent-core
    ├── OpenAILLMClient / OpenAILLMClientFactory
    ├── OpenAICompletion / OpenAICompletionFactory
    └── 依赖 com.openai:openai-java SDK

open-sagent-example
    ├── 依赖 open-sagent-infrastructure (Tool相关)
    └── 依赖 open-sagent-infrastructure-openai (OpenAI相关示例)
```

### 3.2 影响范围

| 模块 | 影响说明 |
|------|----------|
| open-sagent-core | 无变更，不受影响 |
| open-sagent-infrastructure | 删除 OpenAI LLM 相关代码和 SDK 依赖，保留通用基础设施 |
| open-sagent-infrastructure-openai | 新增模块，容纳所有 OpenAI 相关实现 |
| open-sagent-example | 需新增对新模块的依赖；现有示例代码因包路径不变，import 无需修改 |
| open-sagent-tools | 无影响 |
| open-sagent-web | 无影响 |
| open-sagent-cli | 无影响 |

### 3.3 扩展性说明

1. **新增厂商模块范式**：未来新增 Anthropic、Kimi、Qwen 等厂商时，参照 `open-sagent-infrastructure-openai` 创建 `open-sagent-infrastructure-xxx` 模块，实现 `LLMClient` 和 `LLMCompletion` 接口即可
2. **兼容厂商复用**：提供 OpenAI 兼容接口的第三方厂商（DeepSeek、SiliconFlow 等）继续使用 `open-sagent-infrastructure-openai` 中的实现类，只需配置不同的 `baseUrl`
3. **通用与厂商解耦**：`open-sagent-infrastructure` 不再耦合任何特定厂商的 SDK，只负责框架通用能力的落地

## 4. 测试计划

### 4.1 编译验证

```bash
mvn clean compile test-compile -pl open-sagent-infrastructure-openai -am
```

### 4.2 全量编译验证

```bash
mvn clean compile test-compile
```

### 4.3 单元测试

| 测试类 | 模块 | 测试内容 |
|--------|------|----------|
| `OpenAILLMClientFactoryTest` | open-sagent-infrastructure-openai | 工厂参数校验（迁移后功能保持一致） |
| `OpenAICompletionFactoryTest` | open-sagent-infrastructure-openai | 工厂类型校验（迁移后功能保持一致） |

### 4.4 集成测试

| 测试类 | 模块 | 测试内容 |
|--------|------|----------|
| `OpenAILLMClientIntegrationTest` | open-sagent-infrastructure-openai | 真实API连接（迁移后功能保持一致） |
| `OpenAICompletionIntegrationTest` | open-sagent-infrastructure-openai | 真实API对话补全（迁移后功能保持一致） |

## 5. 兼容性说明

- **包路径不变**：OpenAI 实现类的包路径保持 `ai.sagesource.opensagent.infrastructure.llm.openai`，已有的 import 语句无需修改
- **类内容不变**：所有实现类的代码逻辑保持完全一致，行为无差异
- **POM 依赖调整**：使用 OpenAI 实现的模块（如 example）需要在 POM 中显式依赖 `open-sagent-infrastructure-openai`

## 6. 方案变更记录

无

## 7. 评审记录

| 评审人 | 时间 | 结论 | 备注 |
|--------|------|------|------|
| User | 2026-04-14 | 通过 | 同意按方案实施 |
