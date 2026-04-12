---
name: technology-stack
description: 技术栈列表、编码规范
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
    - SLF4J 2.0.17 (logging)

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

### Lombok使用
- 使用 `@Data` 用于POJO
- 使用 `@Builder` 用于构建器模式，对于被`@Builder`标记的类在创建对象时，也要使用`.builder().build()`方法构建对象和参数值
- 使用 `@Getter/@Setter` 精细控制
- 使用 `@Slf4j` 用于日志

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