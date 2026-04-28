---
name: technology-stack
description: 技术栈列表、编码规范、模块依赖规范
---

## 技术栈

- **Language**: Java 17
- **Build Tool**: Maven 3.x
- **Core Dependencies**:
    - Spring Boot 3.5.8 (dependency management)
    - OpenAI Java SDK 4.26.0
    - Lombok 1.18.42
    - Fastjson2 2.0.60
    - dotenv-java 3.2.0 (environment configuration)
    - SLF4J 2.0.17 (logging facade)
    - Logback 1.5.6 (logging implementation, Spring Boot default)

## 编码规范

### 包结构规范
- 基础包名: `ai.sagesource.opensagent.<module-name>`
- 模块内按功能分层: `client/`, `completion/`, `exception/`, `utils/` 等

### 注释规范
- 使用中文编写主要注释和文档
- 类级注释包含 `@author: sage.xue` 和 `@time: YYYY/MM/DD`
- 接口方法注释说明参数和返回值

示例:
```java
/**
 * 测试类
 *
 * @author: sage.xue
 * @time: 2026/3/14
 */
public interface Test {
    /**
     * 定义名称
     *
     * @return
     */
    String name();
}
```

### 命名规范
- 类名: PascalCase (如 `OpenAILLMClient`)
- 方法名: camelCase (如 `thinking_streaming`)
- 常量: UPPER_SNAKE_CASE
- 包名: 全小写
- 子类需要携带父类信息：例如CompletionMessage的子类，需要按照xxxCompletionMessage命名

### Lombok使用
- 使用 `@Data` 用于POJO
- 使用 `@Builder` 用于构建器模式，对于被`@Builder`标记的类在创建对象时，也要使用`.builder().build()`方法构建对象和参数值
- 使用 `@Getter/@Setter` 精细控制
- 使用 `@Slf4j` 用于日志

### 单元测试规范

- 单元测试中需要获取的敏感配置(例如API私钥等)，使用DotEnv实现；DotEnv的使用封装为工具
- 单元测试输出在各个模块的src/test包下，包名和需要单测类的包名一致

### 日志配置规范

- **日志实现**: 使用Logback（Spring Boot默认），通过`logback-spring.xml`配置
- **输出方式**: 统一使用CONSOLE输出，生产环境由容器采集标准输出
- **配置文件**: 每个Spring Boot模块在`src/main/resources/logback-spring.xml`中定义日志配置
- **统一格式**: `%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n`
- **编码**: 统一使用UTF-8

#### logback-spring.xml模板

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n</pattern>
            <charset>UTF-8</charset>
        </encoder>
    </appender>

    <root level="INFO">
        <appender-ref ref="CONSOLE" />
    </root>

    <logger name="ai.sagesource.opensagent" level="INFO" />
    <logger name="org.springframework.web" level="WARN" />
    <logger name="org.hibernate" level="WARN" />
</configuration>
```

### 代码日志规范

- 异常日志使用ERROR级别
- 业务日志使用INFO级别
- 非阻断性问题使用WARN级别
- 日志格式 log.info("> 模块 | 日志内容 <")

### Spring Bean依赖注入规范

- 要使用@Resource注解
- 如果同一个Class有多个Bean时，使用@Qualifier指定名称
- 不能使用@RequiredArgsConstructor注解，通过构造方法注入Bean

示例代码
```java
@Slf4j
@Service  
public class ChatService {
    @Resource
    @Qualifier("transactionTemplate") // 如果同一个Class有多个Bean时，指定名称
    private TransactionTemplate transactionTemplate;
}
```

## 模块依赖规范

- 所有Maven子模块需要在根pom文件中定义
- 子模块依赖其他子模块直接从根pom中继承
- 模块间不允许互相依赖
- 如果依赖的模块已经包含的依赖，不需要再次引用

## Maven依赖版本管理规范

- **所有第三方依赖版本号必须在根pom.xml的`<properties>`中统一定义**，使用`<artifactId>.version`格式的命名规范
- **所有第三方依赖必须在根pom.xml的`<dependencyManagement>`中进行版本锁定**
- **子模块pom.xml中引用依赖时，禁止指定`<version>`标签**，版本由根pom.xml的dependencyManagement统一管理
- Spring Boot BOM已管理的依赖（如spring-boot-starter-web、spring-boot-starter-data-jpa等），可直接引用，无需在dependencyManagement中重复声明

## Example示例代码生成规范

- 涉及到获取敏感配置信息的，例如API-KEY，使用DotEnv模式获取
- 中间过程的功能无需生成Example：例如为LLMClient、Completion的使用生成Example，如何创建Tool无需生成

## Build Commands

### 编译整个项目
```bash
mvn clean compile
```

### 打包项目
```bash
mvn clean package
```

### 安装到本地Maven仓库
```bash
mvn clean install
```

### 清理项目
```bash
mvn clean
```