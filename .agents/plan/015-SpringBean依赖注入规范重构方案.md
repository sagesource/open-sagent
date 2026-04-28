# 015-SpringBean依赖注入规范重构方案

## 1. 背景与目的

### 1.1 背景

当前项目 `open-sagent-web` 模块中的 Spring Bean 依赖注入方式与项目 SKILL 规范存在严重冲突：

- **SKILL 规范要求**（`commons/technology_stack.md` 第103-118行）：
  - 使用 `@Resource` 注解进行依赖注入
  - 多 Bean 场景使用 `@Qualifier` 指定名称
  - **禁止使用 `@RequiredArgsConstructor` 注解，禁止通过构造方法注入 Bean**

- **实际代码现状**：
  - 9 个 Spring Bean 全部使用 `@RequiredArgsConstructor` + `private final` 字段（构造方法注入）
  - `AgentBeanConfig` 中存在 `@Autowired` 字段注入
  - 项目中 **0 处**使用 `@Resource` 注解

### 1.2 目的

将 `open-sagent-web` 模块中所有 Spring Bean 的依赖注入方式统一重构为符合项目 SKILL 规范的 `@Resource` 字段注入模式，确保代码风格与规范一致，消除规范与实现的冲突。

---

## 2. 修改方案

### 2.1 总体策略

1. **移除**所有 `@RequiredArgsConstructor` 注解
2. **移除**所有依赖字段的 `final` 修饰符（`@Resource` 字段注入不支持 `final`）
3. **添加** `@Resource` 注解到所有依赖字段
4. **保留**已有的 `@Qualifier` 注解（多 Bean 限定场景）
5. **`@Autowired` 替换为 `@Resource`**（`AgentBeanConfig` 中的 `PromptProperties`）
6. **`@Value` 配置字段保持不变**（注入的是配置值而非 Bean，不在 Bean 注入规范约束范围内）
7. **测试代码保持不变**（`@Autowired`、`@MockBean`、`@InjectMocks` 为测试框架标准用法）

### 2.2 文件变更清单

共涉及 **10 个文件**，全部位于 `open-sagent-web` 模块：

| 序号 | 文件路径 | 变更类型 |
|------|---------|---------|
| 1 | `service/ChatService.java` | 移除 `@RequiredArgsConstructor`，`final` 改 `private`，添加 `@Resource` |
| 2 | `service/AuthService.java` | 移除 `@RequiredArgsConstructor`，`final` 改 `private`，添加 `@Resource` |
| 3 | `service/ConversationService.java` | 移除 `@RequiredArgsConstructor`，`final` 改 `private`，添加 `@Resource` |
| 4 | `service/TitleAgentService.java` | 移除 `@RequiredArgsConstructor`，`final` 改 `private`，添加 `@Resource` |
| 5 | `controller/ChatController.java` | 移除 `@RequiredArgsConstructor`，`final` 改 `private`，添加 `@Resource` |
| 6 | `controller/AuthController.java` | 移除 `@RequiredArgsConstructor`，`final` 改 `private`，添加 `@Resource` |
| 7 | `controller/ConversationController.java` | 移除 `@RequiredArgsConstructor`，`final` 改 `private`，添加 `@Resource` |
| 8 | `config/WebSecurityConfig.java` | 移除 `@RequiredArgsConstructor`，`final` 改 `private`，添加 `@Resource` |
| 9 | `security/JwtInterceptor.java` | 移除 `@RequiredArgsConstructor`，`final` 改 `private`，添加 `@Resource` |
| 10 | `config/AgentBeanConfig.java` | `@Autowired` 改 `@Resource`，`@Value` 字段保持不变 |

### 2.3 各文件详细变更

#### 2.3.1 ChatService.java

**变更前：**
```java
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    @Qualifier("simpleCompletion")
    private final LLMCompletion simpleCompletion;

    @Qualifier("smartCompletion")
    private final LLMCompletion smartCompletion;

    @Qualifier("simplePromptTemplate")
    private final PromptTemplate simplePromptTemplate;

    @Qualifier("smartPromptTemplate")
    private final PromptTemplate smartPromptTemplate;

    private final ConversationService conversationService;
    private final TitleAgentService titleAgentService;

    @Qualifier("simpleAgentConfig")
    private final AgentConfig simpleAgentConfig;

    @Qualifier("smartAgentConfig")
    private final AgentConfig smartAgentConfig;

    private final Map<String, CompletionCancelToken> activeTokens = new ConcurrentHashMap<>();
    // ...
}
```

**变更后：**
```java
@Slf4j
@Service
public class ChatService {

    @Resource
    @Qualifier("simpleCompletion")
    private LLMCompletion simpleCompletion;

    @Resource
    @Qualifier("smartCompletion")
    private LLMCompletion smartCompletion;

    @Resource
    @Qualifier("simplePromptTemplate")
    private PromptTemplate simplePromptTemplate;

    @Resource
    @Qualifier("smartPromptTemplate")
    private PromptTemplate smartPromptTemplate;

    @Resource
    private ConversationService conversationService;

    @Resource
    private TitleAgentService titleAgentService;

    @Resource
    @Qualifier("simpleAgentConfig")
    private AgentConfig simpleAgentConfig;

    @Resource
    @Qualifier("smartAgentConfig")
    private AgentConfig smartAgentConfig;

    private final Map<String, CompletionCancelToken> activeTokens = new ConcurrentHashMap<>();
    // ...
}
```

> **注意**：`activeTokens` 是内部状态容器（非 Spring 注入依赖），保持 `private final` 不变。

#### 2.3.2 AuthService.java

**变更前：**
```java
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    // ...
}
```

**变更后：**
```java
@Slf4j
@Service
public class AuthService {

    @Resource
    private UserRepository userRepository;

    @Resource
    private PasswordEncoder passwordEncoder;

    @Resource
    private JwtUtil jwtUtil;
    // ...
}
```

#### 2.3.3 ConversationService.java

**变更前：**
```java
@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationService {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    // ...
}
```

**变更后：**
```java
@Slf4j
@Service
public class ConversationService {

    @Resource
    private ConversationRepository conversationRepository;

    @Resource
    private MessageRepository messageRepository;
    // ...
}
```

#### 2.3.4 TitleAgentService.java

**变更前：**
```java
@Slf4j
@Service
@RequiredArgsConstructor
public class TitleAgentService {

    @Qualifier("titleCompletion")
    private final LLMCompletion titleCompletion;

    @Qualifier("titlePromptTemplate")
    private final PromptTemplate titlePromptTemplate;

    @Qualifier("titleAgentConfig")
    private final AgentConfig titleAgentConfig;
    // ...
}
```

**变更后：**
```java
@Slf4j
@Service
public class TitleAgentService {

    @Resource
    @Qualifier("titleCompletion")
    private LLMCompletion titleCompletion;

    @Resource
    @Qualifier("titlePromptTemplate")
    private PromptTemplate titlePromptTemplate;

    @Resource
    @Qualifier("titleAgentConfig")
    private AgentConfig titleAgentConfig;
    // ...
}
```

#### 2.3.5 ChatController.java

**变更前：**
```java
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;
    // ...
}
```

**变更后：**
```java
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    @Resource
    private ChatService chatService;
    // ...
}
```

#### 2.3.6 AuthController.java

**变更前：**
```java
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    // ...
}
```

**变更后：**
```java
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Resource
    private AuthService authService;
    // ...
}
```

#### 2.3.7 ConversationController.java

**变更前：**
```java
@RestController
@RequestMapping("/api/conversations")
@RequiredArgsConstructor
public class ConversationController {

    private final ConversationService conversationService;
    // ...
}
```

**变更后：**
```java
@RestController
@RequestMapping("/api/conversations")
public class ConversationController {

    @Resource
    private ConversationService conversationService;
    // ...
}
```

#### 2.3.8 WebSecurityConfig.java

**变更前：**
```java
@Configuration
@RequiredArgsConstructor
public class WebSecurityConfig implements WebMvcConfigurer {

    private final JwtInterceptor jwtInterceptor;
    // ...
}
```

**变更后：**
```java
@Configuration
public class WebSecurityConfig implements WebMvcConfigurer {

    @Resource
    private JwtInterceptor jwtInterceptor;
    // ...
}
```

#### 2.3.9 JwtInterceptor.java

**变更前：**
```java
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;
    // ...
}
```

**变更后：**
```java
@Slf4j
@Component
public class JwtInterceptor implements HandlerInterceptor {

    @Resource
    private JwtUtil jwtUtil;
    // ...
}
```

#### 2.3.10 AgentBeanConfig.java

**变更前：**
```java
@Slf4j
@Configuration
@EnableConfigurationProperties(PromptProperties.class)
public class AgentBeanConfig {

    // ... 16个@Value字段保持不变 ...

    @Autowired
    private PromptProperties promptProperties;
    // ...
}
```

**变更后：**
```java
@Slf4j
@Configuration
@EnableConfigurationProperties(PromptProperties.class)
public class AgentBeanConfig {

    // ... 16个@Value字段保持不变 ...

    @Resource
    private PromptProperties promptProperties;
    // ...
}
```

> **说明**：`AgentBeanConfig` 中的 `@Value` 字段注入的是配置属性值（`String`、`Integer`、`Double` 等），不是 Spring Bean，因此不在 Bean 注入规范约束范围内，保持原样。

### 2.4 import 调整

所有涉及文件需要统一调整 import：

- **移除**：`lombok.RequiredArgsConstructor`、`org.springframework.beans.factory.annotation.Autowired`（如存在）
- **添加**：`jakarta.annotation.Resource`
- **保留**：`org.springframework.beans.factory.annotation.Qualifier`、`org.springframework.beans.factory.annotation.Value`（如存在）

---

## 3. 影响范围分析

### 3.1 功能影响

- **无功能变更**：仅改变依赖注入方式，不修改任何业务逻辑
- **无 API 变更**：所有 Controller、Service 的公共接口保持不变
- **无配置变更**：`application.properties` / `application.yml` 无需调整

### 3.2 行为一致性分析

| 方面 | 原方式（构造注入） | 新方式（@Resource 字段注入） | 是否等价 |
|------|------------------|---------------------------|---------|
| 依赖注入时机 | 实例化时（构造参数） | 实例化后（字段赋值） | ✅ 等价，Spring 均保证在 Bean 使用前完成注入 |
| 循环依赖 | 构造注入天然避免循环依赖 | 字段注入支持循环依赖 | ✅ 当前项目无循环依赖，不影响 |
| 可选依赖 | 构造注入不支持 `required=false` | `@Resource` 支持 `required=false` | ✅ 当前所有依赖均为强依赖 |
| 不可变性 | `final` 字段保证不可变 | 非 `final` 字段 | ⚠️ 字段可被重新赋值（但实际由 Spring 管理，无外部直接赋值场景） |
| 空安全性 | 构造注入在编译期强制非空 | 字段注入运行期检查 | ⚠️ 两者在 Spring 容器管理下运行时行为一致 |

### 3.3 测试影响

- **单元测试**：`AuthServiceTest` 使用 `@InjectMocks` + `@Mock`，Mockito 的 `@InjectMocks` 支持字段注入，**无需修改**
- **集成测试**：`AuthControllerTest` 使用 `@WebMvcTest` + `@MockBean`，**无需修改**
- **JwtUtilTest**：使用 `ReflectionTestUtils.setField` 设置 `private` 字段，重构后字段仍为非 `final`，**无需修改**

### 3.4 模块影响

仅影响 `open-sagent-web` 模块，其他模块（`base`、`core`、`infrastructure`、`infrastructure-openai`、`example`、`tools`）**不受影响**。

---

## 4. 测试计划

### 4.1 编译验证

```bash
mvn clean compile -pl open-sagent-web
```

### 4.2 单元测试验证

```bash
mvn test -pl open-sagent-web
```

### 4.3 全量编译验证

```bash
mvn clean compile
```

### 4.4 打包验证

```bash
mvn clean package -DskipTests
```

### 4.5 应用启动验证

```bash
cd open-sagent-web
mvn spring-boot:run
```

验证接口正常：
- `POST /api/auth/register`
- `POST /api/auth/login`
- `GET /api/conversations`
- `GET /api/chat/stream`

---

## 5. 方案变更记录

| 版本 | 日期 | 变更内容 | 变更人 |
|------|------|---------|--------|
| v1.0 | 2026/4/28 | 初始方案 | Kimi Code CLI |

---

## 6. 评审记录

| 评审轮次 | 评审人 | 评审结论 | 评审意见 | 评审日期 |
|---------|--------|---------|---------|---------|
| 第一轮 | 用户 | ✅通过 | 同意 | 2026/4/28 |
