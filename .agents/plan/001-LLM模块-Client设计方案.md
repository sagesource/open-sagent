# 方案：001-LLM模块-Client设计方案

## 1. 背景与目的

### 1.1 背景

Open Sagent是一个AI-Agent框架，需要与各大厂商的大语言模型（LLM）进行交互。为了屏蔽不同厂商（如OpenAI、Anthropic、Azure等）的API差异，需要在Core模块中定义统一的Client抽象接口，并在Infrastructure模块中提供基于OpenAI SDK的具体实现。

### 1.2 目的

1. 设计可扩展的LLM Client抽象架构，支持多厂商LLM接入
2. 定义统一的Client配置模型，支持参数化构建
3. 建立统一的异常处理机制
4. 为后续Completion、Message、Memory等模块提供基础

## 2. 修改方案

### 2.1 文件变更列表

| 文件路径 | 变更类型 | 说明 |
|----------|----------|------|
| `open-sagent-base/src/main/java/ai/sagesource/opensagent/base/exception/OpenSagentException.java` | 新增 | 全局异常基类 |
| `open-sagent-base/src/main/java/ai/sagesource/opensagent/base/utils/DotEnvUtils.java` | 新增 | DotEnv工具类（读取敏感配置） |
| `open-sagent-core/src/main/java/ai/sagesource/opensagent/core/llm/client/LLMClient.java` | 新增 | LLM客户端抽象接口 |
| `open-sagent-core/src/main/java/ai/sagesource/opensagent/core/llm/client/LLMClientConfig.java` | 新增 | Client配置参数类 |
| `open-sagent-core/src/main/java/ai/sagesource/opensagent/core/llm/exception/OpenSagentLLMException.java` | 新增 | LLM模块异常类（继承OpenSagentException） |
| `open-sagent-infrastructure/src/main/java/ai/sagesource/opensagent/infrastructure/llm/openai/OpenAILLMClient.java` | 新增 | OpenAI Client实现类 |
| `open-sagent-infrastructure/src/main/java/ai/sagesource/opensagent/infrastructure/llm/openai/OpenAILLMClientFactory.java` | 新增 | OpenAI Client工厂类 |
| `open-sagent-infrastructure/src/test/java/ai/sagesource/opensagent/infrastructure/llm/openai/OpenAILLMClientFactoryTest.java` | 新增 | 工厂类单元测试 |
| `open-sagent-infrastructure/src/test/java/ai/sagesource/opensagent/infrastructure/llm/openai/OpenAILLMClientIntegrationTest.java` | 新增 | OpenAI集成测试（可选） |

### 2.2 详细变更内容

#### 文件 2: `open-sagent-base/src/main/java/ai/sagesource/opensagent/base/exception/OpenSagentException.java`

```java
package ai.sagesource.opensagent.base.exception;

/**
 * OpenSagent全局异常基类
 * <p>
 * 所有自定义异常都必须继承此类
 *
 * @author: sage.xue
 * @time: 2026/4/13
 */
public class OpenSagentException extends RuntimeException {

    public OpenSagentException(String message) {
        super(message);
    }

    public OpenSagentException(String message, Throwable cause) {
        super(message, cause);
    }

    public OpenSagentException(Throwable cause) {
        super(cause);
    }
}
```

#### 文件 2: `open-sagent-core/src/main/java/ai/sagesource/opensagent/core/llm/exception/OpenSagentLLMException.java`

```java
package ai.sagesource.opensagent.core.llm.exception;

import ai.sagesource.opensagent.base.exception.OpenSagentException;

/**
 * LLM模块异常类
 *
 * @author: sage.xue
 * @time: 2026/4/13
 */
public class OpenSagentLLMException extends OpenSagentException {

    public OpenSagentLLMException(String message) {
        super(message);
    }

    public OpenSagentLLMException(String message, Throwable cause) {
        super(message, cause);
    }

    public OpenSagentLLMException(Throwable cause) {
        super(cause);
    }
}
```

#### 文件 3: `open-sagent-core/src/main/java/ai/sagesource/opensagent/core/llm/client/LLMClientConfig.java`

```java
package ai.sagesource.opensagent.core.llm.client;

import lombok.Builder;
import lombok.Data;

import java.time.Duration;

/**
 * LLM客户端配置参数类
 * <p>
 * 通用配置，各厂商实现根据需要使用相应字段
 *
 * @author: sage.xue
 * @time: 2026/4/13
 */
@Data
@Builder
public class LLMClientConfig {

    /**
     * API密钥
     */
    private String apiKey;

    /**
     * 基础URL（可选，用于自定义端点或代理）
     */
    private String baseUrl;

    /**
     * 模型名称
     */
    private String model;

    /**
     * 连接超时时间
     */
    @Builder.Default
    private Duration connectTimeout = Duration.ofSeconds(10);

    /**
     * 读取超时时间
     */
    @Builder.Default
    private Duration readTimeout = Duration.ofSeconds(60);

    /**
     * 代理主机（可选）
     */
    private String proxyHost;

    /**
     * 代理端口（可选）
     */
    private Integer proxyPort;

    /**
     * 组织ID（可选，用于OpenAI等）
     */
    private String organizationId;

    /**
     * 项目ID（可选，用于OpenAI等）
     */
    private String projectId;
}
```

#### 文件 4: `open-sagent-core/src/main/java/ai/sagesource/opensagent/core/llm/client/LLMClient.java`

```java
package ai.sagesource.opensagent.core.llm.client;

/**
 * LLM客户端抽象接口
 * <p>
 * 定义与LLM厂商交互的统一接口，屏蔽底层实现差异。
 * 不同厂商的实现通过具体的工厂类区分（如OpenAILLMClientFactory、AnthropicLLMClientFactory等）
 *
 * @author: sage.xue
 * @time: 2026/4/13
 */
public interface LLMClient {

    /**
     * 获取客户端配置
     *
     * @return 配置对象
     */
    LLMClientConfig getConfig();

    /**
     * 测试连接是否可用
     *
     * @return true表示连接正常
     */
    boolean isHealthy();

    /**
     * 关闭客户端，释放资源
     */
    void close();
}
```

#### 文件 5: `open-sagent-infrastructure/src/main/java/ai/sagesource/opensagent/infrastructure/llm/openai/OpenAILLMClient.java`

```java
package ai.sagesource.opensagent.infrastructure.llm.openai;

import ai.sagesource.opensagent.core.llm.client.LLMClient;
import ai.sagesource.opensagent.core.llm.client.LLMClientConfig;
import ai.sagesource.opensagent.core.llm.exception.OpenSagentLLMException;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import lombok.extern.slf4j.Slf4j;

import java.net.InetSocketAddress;
import java.net.Proxy;

/**
 * OpenAI LLM客户端实现
 *
 * @author: sage.xue
 * @time: 2026/4/13
 */
@Slf4j
public class OpenAILLMClient implements LLMClient {

    private final LLMClientConfig config;
    private final OpenAIClient openAIClient;

    /**
     * 构造方法，基于配置创建OpenAI客户端
     *
     * @param config 客户端配置
     * @throws OpenSagentLLMException 创建失败时抛出
     */
    public OpenAILLMClient(LLMClientConfig config) {
        this.config = config;
        try {
            this.openAIClient = buildClient(config);
            log.debug("OpenAI LLM客户端创建成功，模型: {}", config.getModel());
        } catch (Exception e) {
            log.error("创建OpenAI LLM客户端失败: {}", e.getMessage(), e);
            throw new OpenSagentLLMException("创建OpenAI LLM客户端失败: " + e.getMessage(), e);
        }
    }

    /**
     * 构建OpenAI客户端
     *
     * @param config 配置参数
     * @return OpenAIClient实例
     */
    private OpenAIClient buildClient(LLMClientConfig config) {
        OpenAIOkHttpClient.Builder builder = OpenAIOkHttpClient.builder();

        // 设置API密钥
        if (config.getApiKey() != null && !config.getApiKey().isEmpty()) {
            builder.apiKey(config.getApiKey());
        }

        // 设置基础URL（用于代理或自定义端点）
        if (config.getBaseUrl() != null && !config.getBaseUrl().isEmpty()) {
            builder.baseUrl(config.getBaseUrl());
        }

        // 设置组织ID
        if (config.getOrganizationId() != null && !config.getOrganizationId().isEmpty()) {
            builder.organization(config.getOrganizationId());
        }

        // 设置项目ID
        if (config.getProjectId() != null && !config.getProjectId().isEmpty()) {
            builder.project(config.getProjectId());
        }

        // 设置超时
        if (config.getConnectTimeout() != null) {
            builder.timeout(config.getConnectTimeout());
        }

        // 设置代理
        if (config.getProxyHost() != null && !config.getProxyHost().isEmpty()
                && config.getProxyPort() != null) {
            Proxy proxy = new Proxy(Proxy.Type.HTTP,
                    new InetSocketAddress(config.getProxyHost(), config.getProxyPort()));
            builder.proxy(proxy);
        }

        return builder.build();
    }

    /**
     * 获取底层的OpenAI客户端（供内部使用）
     *
     * @return OpenAIClient实例
     */
    OpenAIClient getOpenAIClient() {
        return openAIClient;
    }

    @Override
    public LLMClientConfig getConfig() {
        return config;
    }

    @Override
    public boolean isHealthy() {
        try {
            // 通过简单的模型列表请求验证连接
            openAIClient.models().list();
            return true;
        } catch (Exception e) {
            log.warn("OpenAI连接健康检查失败: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public void close() {
        // OpenAIClient不需要显式关闭，但保留接口以便扩展
        log.debug("OpenAI LLM客户端关闭");
    }
}
```

#### 文件 6: `open-sagent-base/src/main/java/ai/sagesource/opensagent/base/utils/DotEnvUtils.java`

```java
package ai.sagesource.opensagent.base.utils;

import io.github.cdimascio.dotenv.Dotenv;

/**
 * DotEnv工具类
 * <p>
 * 封装DotEnv配置读取，用于获取敏感配置如API密钥等
 *
 * @author: sage.xue
 * @time: 2026/4/13
 */
public class DotEnvUtils {

    private static final Dotenv dotenv = Dotenv.configure()
            .ignoreIfMissing()
            .load();

    /**
     * 获取环境变量值
     *
     * @param key 变量名
     * @return 变量值，不存在则返回null
     */
    public static String get(String key) {
        // 优先从系统环境变量获取
        String value = System.getenv(key);
        if (value != null && !value.isEmpty()) {
            return value;
        }
        // 从.env文件获取
        return dotenv.get(key);
    }

    /**
     * 获取环境变量值，带默认值
     *
     * @param key          变量名
     * @param defaultValue 默认值
     * @return 变量值，不存在则返回默认值
     */
    public static String get(String key, String defaultValue) {
        String value = get(key);
        return value != null ? value : defaultValue;
    }
}
```

#### 文件 7: `open-sagent-infrastructure/src/main/java/ai/sagesource/opensagent/infrastructure/llm/openai/OpenAILLMClientFactory.java`

```java
package ai.sagesource.opensagent.infrastructure.llm.openai;

import ai.sagesource.opensagent.core.llm.client.LLMClient;
import ai.sagesource.opensagent.core.llm.client.LLMClientConfig;
import ai.sagesource.opensagent.core.llm.exception.OpenSagentLLMException;

/**
 * OpenAI LLM客户端工厂类
 *
 * @author: sage.xue
 * @time: 2026/4/13
 */
public class OpenAILLMClientFactory {

    /**
     * 创建OpenAI客户端
     *
     * @param config 客户端配置
     * @return LLMClient实例
     * @throws OpenSagentLLMException 当配置无效或创建失败时抛出
     */
    public static LLMClient createClient(LLMClientConfig config) {
        validateConfig(config);
        return new OpenAILLMClient(config);
    }

    /**
     * 验证配置参数
     *
     * @param config 配置对象
     * @throws OpenSagentLLMException 配置无效时抛出
     */
    private static void validateConfig(LLMClientConfig config) {
        if (config == null) {
            throw new OpenSagentLLMException("LLMClientConfig不能为空");
        }
        if (config.getApiKey() == null || config.getApiKey().isEmpty()) {
            throw new OpenSagentLLMException("API密钥不能为空");
        }
        if (config.getModel() == null || config.getModel().isEmpty()) {
            throw new OpenSagentLLMException("模型名称不能为空");
        }
    }

    /**
     * 使用默认配置快速创建客户端
     *
     * @param apiKey API密钥
     * @param model  模型名称
     * @return LLMClient实例
     */
    public static LLMClient createClient(String apiKey, String model) {
        LLMClientConfig config = LLMClientConfig.builder()
                .apiKey(apiKey)
                .model(model)
                .build();
        return createClient(config);
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
    ├── LLMClient (接口)
    ├── LLMClientConfig (配置)
    └── OpenSagentLLMException (继承OpenSagentException)

open-sagent-infrastructure (具体实现)
    ├── 依赖 open-sagent-core
    ├── OpenAILLMClient (OpenAI实现)
    └── OpenAILLMClientFactory (工厂)
```

### 3.2 影响范围

| 模块 | 影响说明 |
|------|----------|
| open-sagent-core | 新增LLM Client核心抽象，无破坏性变更 |
| open-sagent-infrastructure | 新增OpenAI实现，无破坏性变更 |
| open-sagent-tools | 无直接影响，后续可使用LLM Client |
| open-sagent-web | 无直接影响 |
| open-sagent-cli | 无直接影响 |

### 3.3 扩展性说明

1. **支持新增厂商**：通过实现`LLMClient`接口并在Infrastructure模块添加对应的工厂类（如AnthropicLLMClientFactory），可支持多厂商接入
2. **配置扩展**：`LLMClientConfig`使用Builder模式，便于后续添加新配置项
3. **工厂模式**：每个厂商提供独立的Factory，便于管理和扩展，无需ProviderType枚举区分

## 4. 测试计划

### 4.1 单元测试

| 测试类 | 测试内容 |
|--------|----------|
| `LLMClientConfigTest` | 配置对象构建、默认值验证 |
| `OpenSagentExceptionTest` | 全局异常基类构造方法测试 |
| `OpenSagentLLMExceptionTest` | LLM异常继承关系验证 |
| `OpenAILLMClientFactoryTest` | 工厂方法参数验证、异常场景 |

### 4.2 集成测试

| 测试类 | 测试内容 |
|--------|----------|
| `OpenAILLMClientIntegrationTest` | 真实API连接（可选，需配置API Key） |

### 4.3 测试示例

#### 文件: `open-sagent-infrastructure/src/test/java/ai/sagesource/opensagent/infrastructure/llm/openai/OpenAILLMClientFactoryTest.java`

```java
package ai.sagesource.opensagent.infrastructure.llm.openai;

import ai.sagesource.opensagent.core.llm.client.LLMClient;
import ai.sagesource.opensagent.core.llm.client.LLMClientConfig;
import ai.sagesource.opensagent.core.llm.exception.OpenSagentLLMException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * OpenAILLMClientFactory单元测试
 *
 * @author: sage.xue
 * @time: 2026/4/13
 */
class OpenAILLMClientFactoryTest {

    @Test
    @DisplayName("使用有效配置创建客户端 - 成功")
    void testCreateClientWithValidConfig() {
        // 构造配置
        LLMClientConfig config = LLMClientConfig.builder()
                .apiKey("test-api-key")
                .model("gpt-4")
                .build();

        // 执行
        LLMClient client = OpenAILLMClientFactory.createClient(config);

        // 验证
        assertNotNull(client, "客户端不应为空");
        assertNotNull(client.getConfig(), "配置不应为空");
        assertEquals("gpt-4", client.getConfig().getModel(), "模型名称应一致");
    }

    @Test
    @DisplayName("使用快速方法创建客户端 - 成功")
    void testCreateClientWithQuickMethod() {
        // 执行
        LLMClient client = OpenAILLMClientFactory.createClient("test-api-key", "gpt-3.5-turbo");

        // 验证
        assertNotNull(client, "客户端不应为空");
        assertEquals("gpt-3.5-turbo", client.getConfig().getModel(), "模型名称应一致");
    }

    @Test
    @DisplayName("配置为null - 抛出异常")
    void testCreateClientWithNullConfig() {
        // 执行并验证
        OpenSagentLLMException exception = assertThrows(
                OpenSagentLLMException.class,
                () -> OpenAILLMClientFactory.createClient(null),
                "配置为null时应抛出异常"
        );
        assertTrue(exception.getMessage().contains("不能为空"));
    }

    @Test
    @DisplayName("API密钥为空 - 抛出异常")
    void testCreateClientWithEmptyApiKey() {
        // 构造配置
        LLMClientConfig config = LLMClientConfig.builder()
                .apiKey("")
                .model("gpt-4")
                .build();

        // 执行并验证
        OpenSagentLLMException exception = assertThrows(
                OpenSagentLLMException.class,
                () -> OpenAILLMClientFactory.createClient(config),
                "API密钥为空时应抛出异常"
        );
        assertTrue(exception.getMessage().contains("API密钥不能为空"));
    }

    @Test
    @DisplayName("模型名称为空 - 抛出异常")
    void testCreateClientWithEmptyModel() {
        // 构造配置
        LLMClientConfig config = LLMClientConfig.builder()
                .apiKey("test-api-key")
                .model("")
                .build();

        // 执行并验证
        OpenSagentLLMException exception = assertThrows(
                OpenSagentLLMException.class,
                () -> OpenAILLMClientFactory.createClient(config),
                "模型名称为空时应抛出异常"
        );
        assertTrue(exception.getMessage().contains("模型名称不能为空"));
    }
}
```

#### 文件: `open-sagent-infrastructure/src/test/java/ai/sagesource/opensagent/infrastructure/llm/openai/OpenAILLMClientIntegrationTest.java`

```java
package ai.sagesource.opensagent.infrastructure.llm.openai;

import ai.sagesource.opensagent.core.llm.client.LLMClient;
import ai.sagesource.opensagent.core.llm.client.LLMClientConfig;
import ai.sagesource.opensagent.base.utils.DotEnvUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.junit.jupiter.api.Assertions.*;

/**
 * OpenAILLMClient集成测试
 * <p>
 * 需要真实API密钥，通过DotEnv获取
 *
 * @author: sage.xue
 * @time: 2026/4/13
 */
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class OpenAILLMClientIntegrationTest {

    private static String apiKey;
    private static final String MODEL = "gpt-4o-mini";

    @BeforeAll
    static void setUp() {
        // 通过DotEnv获取敏感配置
        apiKey = DotEnvUtils.get("OPENAI_API_KEY");
    }

    @Test
    @DisplayName("连接健康检查 - 成功")
    void testIsHealthy() {
        // 跳过测试如果未配置API密钥
        if (apiKey == null || apiKey.isEmpty()) {
            return;
        }

        // 构造客户端
        LLMClient client = OpenAILLMClientFactory.createClient(apiKey, MODEL);

        // 执行并验证
        assertTrue(client.isHealthy(), "连接应正常");
    }

    @Test
    @DisplayName("完整配置创建客户端 - 成功")
    void testCreateClientWithFullConfig() {
        // 跳过测试如果未配置API密钥
        if (apiKey == null || apiKey.isEmpty()) {
            return;
        }

        // 构造完整配置
        LLMClientConfig config = LLMClientConfig.builder()
                .apiKey(apiKey)
                .model(MODEL)
                .baseUrl("https://api.openai.com/v1")
                .build();

        // 执行
        LLMClient client = OpenAILLMClientFactory.createClient(config);

        // 验证
        assertNotNull(client);
        assertTrue(client.isHealthy());
    }
}
```

#### 文件: `open-sagent-base/src/main/java/ai/sagesource/opensagent/base/utils/DotEnvUtils.java`

```java
package ai.sagesource.opensagent.base.utils;

import io.github.cdimascio.dotenv.Dotenv;

/**
 * DotEnv工具类
 * <p>
 * 封装DotEnv配置读取，用于获取敏感配置如API密钥等
 *
 * @author: sage.xue
 * @time: 2026/4/13
 */
public class DotEnvUtils {

    private static final Dotenv dotenv = Dotenv.configure()
            .ignoreIfMissing()
            .load();

    /**
     * 获取环境变量值
     *
     * @param key 变量名
     * @return 变量值，不存在则返回null
     */
    public static String get(String key) {
        // 优先从系统环境变量获取
        String value = System.getenv(key);
        if (value != null && !value.isEmpty()) {
            return value;
        }
        // 从.env文件获取
        return dotenv.get(key);
    }

    /**
     * 获取环境变量值，带默认值
     *
     * @param key          变量名
     * @param defaultValue 默认值
     * @return 变量值，不存在则返回默认值
     */
    public static String get(String key, String defaultValue) {
        String value = get(key);
        return value != null ? value : defaultValue;
    }
}
```

## 5. 方案变更记录

### 变更 1（2026-04-13）：修复异常体系，添加OpenSagentException基类

**变更原因：**
1. `OpenSagentLLMException` 原继承 `RuntimeException`，不符合项目异常体系要求
2. 所有自定义异常必须继承 `OpenSagentException` 基类

**文件变更：**

| 文件路径 | 变更类型 | 说明 |
|----------|----------|------|
| `open-sagent-base/.../OpenSagentException.java` | 新增 | 新增全局异常基类 |
| `open-sagent-core/.../OpenSagentLLMException.java` | 修改 | 继承改为 `OpenSagentException` |

**关键代码变更：**
```java
// 修改前
public class OpenSagentLLMException extends RuntimeException { }

// 修改后
public class OpenSagentLLMException extends OpenSagentException { }
```

### 变更 2（2026-04-13）：移除LLMProviderType枚举

**变更原因：**
1. 不需要通过枚举区分厂商类型
2. 不同厂商通过不同的工厂类（如OpenAILLMClientFactory、AnthropicLLMClientFactory）区分即可

**文件变更：**

| 文件路径 | 变更类型 | 说明 |
|----------|----------|------|
| `open-sagent-core/.../LLMProviderType.java` | 删除 | 移除枚举定义 |
| `open-sagent-core/.../LLMClientConfig.java` | 修改 | 移除 `providerType` 字段 |
| `open-sagent-core/.../LLMClient.java` | 修改 | 移除 `getProviderType()` 方法 |
| `open-sagent-infrastructure/.../OpenAILLMClient.java` | 修改 | 移除 `getProviderType()` 实现和相关导入 |
| `open-sagent-infrastructure/.../OpenAILLMClientFactory.java` | 修改 | 移除对 `providerType` 的验证 |

## 6. 评审记录

| 评审人 | 时间 | 结论 | 备注 |
|--------|------|------|------|
| User | 2026-04-13 | 需修改 | 问题1：OpenSagentLLMException不符合异常体系，需要继承OpenSagentException基类，基类定义在base模块 |
| User | 2026-04-13 | 需修改 | 问题2：LLMProviderType不需要，基于不同Provider生成不同实现即可 |
| User | 2026-04-13 | 通过 | 方案通过，可以实施代码变更 |
