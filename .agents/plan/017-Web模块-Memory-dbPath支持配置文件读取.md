# 方案：017-Web模块-Memory-dbPath支持配置文件读取

## 1. 背景与目的

当前 Web 模块中，`MultipleSQLLiteMemory` 的 `dbPath` 参数存在以下问题：

1. **硬编码问题**：`ChatService.java` 中直接硬编码了 `"./memory.db"`，无法通过外部配置调整。
2. **配置不一致**：`TitleAgentService.java` 中调用无参构造，使用 `MultipleSQLLiteMemory` 内部默认值 `"memory.db"`，与 `ChatService` 的写法不统一。
3. **不符合架构规范**：根据 `project_design_web.md` SKILL 约定——"后端使用Spring-Boot，初始化Agent所需的参数均支持从Spring配置文件中获取"，Memory 的 dbPath 也应纳入 Spring 配置管理。

本次变更旨在将 Memory 的 `dbPath` 参数从 `application.yml` 配置文件中读取，支持通过环境变量覆盖，提升配置灵活性和一致性。

## 2. 修改方案

### 2.1 文件变更列表

| 文件路径 | 变更类型 | 说明 |
|----------|----------|------|
| `open-sagent-web/src/main/resources/application.yml` | 修改 | 新增 `sagent.memory.db-path` 配置项 |
| `open-sagent-web/src/main/java/ai/sagesource/opensagent/web/service/ChatService.java` | 修改 | 使用 `@Value` 注入 dbPath，替换硬编码 |
| `open-sagent-web/src/main/java/ai/sagesource/opensagent/web/service/TitleAgentService.java` | 修改 | 使用 `@Value` 注入 dbPath，替换默认构造 |

### 2.2 详细变更内容

#### 文件 1: application.yml

在 `sagent` 配置节点下新增 `memory` 配置：

```yaml
sagent:
  # 新增 Memory 配置
  memory:
    db-path: ${SAGENT_MEMORY_DB_PATH:./memory.db}
```

配置说明：
- 配置项路径：`sagent.memory.db-path`
- 默认值：`./memory.db`（保持与当前硬编码值一致，向后兼容）
- 支持通过环境变量 `SAGENT_MEMORY_DB_PATH` 覆盖

#### 文件 2: ChatService.java

新增 `@Value` 注入字段，并替换硬编码：

```java
@Slf4j
@Service
public class ChatService {

    // ... 现有依赖注入保持不变 ...

    @Value("${sagent.memory.db-path:./memory.db}")
    private String memoryDbPath;

    // ...

    public SseEmitter streamChat(Long userId, String sessionId, String message, String agentVersion) {
        // ... 现有逻辑保持不变 ...

        LLMCompletion memoryCompletion = "smart".equals(agentVersion) ? smartCompletion : simpleCompletion;
        Memory memory = new MultipleSQLLiteMemory(
                sessionId,
                memoryDbPath,
                50,
                memoryCompletion,
                null
        );

        // ... 现有逻辑保持不变 ...
    }
}
```

#### 文件 3: TitleAgentService.java

新增 `@Value` 注入字段，并替换默认构造：

```java
@Slf4j
@Service
public class TitleAgentService {

    // ... 现有依赖注入保持不变 ...

    @Value("${sagent.memory.db-path:./memory.db}")
    private String memoryDbPath;

    // ...

    public String generateTitle(String userMessage, boolean isFirstMessage) {
        // ...

        try {
            MultipleSQLLiteMemory memory = new MultipleSQLLiteMemory(
                    "title-agent-" + System.currentTimeMillis(),
                    memoryDbPath,
                    50,
                    null,
                    null
            );

            // ... 现有逻辑保持不变 ...
        }
    }
}
```

**关于 TitleAgentService 的 windowSize 参数说明**：
- 标题 Agent 为一次性调用场景，不依赖历史窗口压缩能力
- `windowSize` 传 `50` 与 `MultipleSQLLiteMemory` 的默认窗口大小一致
- `completion` 和 `fallbackStrategy` 传 `null`，保持原有行为（无压缩策略）

## 3. 影响范围分析

| 影响点 | 说明 |
|--------|------|
| **向后兼容性** | 默认值为 `./memory.db`，与当前硬编码值一致，无破坏性变更 |
| **环境变量支持** | 新增 `SAGENT_MEMORY_DB_PATH` 环境变量，便于容器化部署时灵活挂载数据卷 |
| **配置一致性** | ChatService 和 TitleAgentService 统一从同一配置项读取，消除不一致问题 |
| **其他模块** | 仅影响 `open-sagent-web` 模块，`MultipleSQLLiteMemory` 本身无需修改 |

## 4. 测试计划

1. **本地启动验证**：
   - 不配置 `sagent.memory.db-path`，验证默认 `./memory.db` 正常工作
   - 配置 `SAGENT_MEMORY_DB_PATH=/tmp/test-memory.db`，验证路径生效

2. **功能验证**：
   - 发起流式对话，确认 Memory 正常读写
   - 触发标题生成，确认 TitleAgent 的 Memory 正常工作

3. **兼容性验证**：
   - 确认原有 `.env` 文件无需变更即可正常运行

## 5. 评审记录

| 评审人 | 时间 | 结论 | 备注 |
|--------|------|------|------|
| Owner | 2026-04-28 | 通过 | |
