# 方案：011-Agent模块-MultipleSQLLiteMemory设计方案

## 1. 背景与目的

### 1.1 背景

Open Sagent框架已具备基于内存的 `SimpleMemory` 实现，提供了滑动窗口压缩、SYSTEM消息过滤、TOOL消息去重等基础能力。但在实际生产环境中，`SimpleMemory` 存在以下局限：

1. **数据易失性**：进程重启后所有对话历史与压缩记忆全部丢失，无法满足长周期对话场景需求。
2. **单会话限制**：`SimpleMemory` 实例仅服务于单一对话窗，缺乏多会话隔离与持久化管理能力。
3. **压缩能力有限**：现有压缩基于简单拼接规则，缺乏语义理解和信息提炼能力，压缩质量较低。

根据 `project_design_agent.md` 中 Memory 模块的架构规划，`infrastructure` 层需要提供 `MultipleSQLLiteMemory` 实现，具备以下特性：

- 在 `SimpleMemory` 的基础上，将会话历史、压缩后的记忆持久化在 SQLite 中。
- 注入一个 `LLMCompletion`，通过调用大模型完成智能记忆压缩。

### 1.2 目的

1. 设计基于 SQLite 的持久化 Memory 实现，支持多会话隔离与数据恢复。
2. 设计基于 LLM 的智能记忆压缩策略，通过 Prompt 工程实现高质量语义压缩。
3. 保留 `SimpleMemory` 的滑动窗口、SYSTEM 消息过滤、TOOL 消息去重等核心能力。
4. 提供 `CompletionMessage` 的 JSON 序列化/反序列化方案，确保多态消息对象的持久化兼容性。

## 2. 修改方案

### 2.1 模块职责边界

```
open-sagent-core (抽象定义层，无变更)
    ├── Memory                        Memory 抽象接口
    ├── MemoryItem                    记忆项模型
    ├── CompressionResult             压缩结果模型
    ├── MemoryCompressionStrategy     记忆压缩策略接口
    └── CompletionMessage             消息基类（多态序列化依赖）

open-sagent-infrastructure (具体实现层)
    ├── MultipleSQLLiteMemory              基于 SQLite 的持久化 Memory 实现
    ├── LLMMemoryCompressionStrategy       基于 LLM 的智能压缩策略
    └── MessageJsonUtils                   CompletionMessage JSON 序列化工具
```

### 2.2 文件变更列表

#### open-sagent-infrastructure

| 文件路径 | 变更类型 | 说明 |
|----------|----------|------|
| `open-sagent-infrastructure/pom.xml` | 修改 | 新增 sqlite-jdbc、fastjson2 依赖 |
| `open-sagent-infrastructure/src/main/java/ai/sagesource/opensagent/infrastructure/agent/memory/MessageJsonUtils.java` | 新增 | CompletionMessage JSON 序列化/反序列化工具（基于 fastjson2 autoType） |
| `open-sagent-infrastructure/src/main/java/ai/sagesource/opensagent/infrastructure/agent/memory/LLMMemoryCompressionStrategy.java` | 新增 | 基于 LLM 的智能记忆压缩策略，通过 Prompt 调用大模型完成语义压缩 |
| `open-sagent-infrastructure/src/main/java/ai/sagesource/opensagent/infrastructure/agent/memory/MultipleSQLLiteMemory.java` | 新增 | 基于 SQLite 的持久化 Memory 实现，支持多会话隔离 |
| `open-sagent-infrastructure/src/test/java/ai/sagesource/opensagent/infrastructure/agent/memory/MessageJsonUtilsTest.java` | 新增 | MessageJsonUtils 单元测试 |
| `open-sagent-infrastructure/src/test/java/ai/sagesource/opensagent/infrastructure/agent/memory/LLMMemoryCompressionStrategyTest.java` | 新增 | LLM 压缩策略单元测试（基于 Mock Completion） |
| `open-sagent-infrastructure/src/test/java/ai/sagesource/opensagent/infrastructure/agent/memory/MultipleSQLLiteMemoryTest.java` | 新增 | MultipleSQLLiteMemory 单元测试 |

### 2.3 详细变更内容

#### 文件 1: `open-sagent-infrastructure/pom.xml`

在 `<dependencies>` 节点内新增以下依赖：

```xml
<!-- SQLite JDBC -->
<dependency>
    <groupId>org.xerial</groupId>
    <artifactId>sqlite-jdbc</artifactId>
    <version>3.45.2.0</version>
</dependency>

<!-- Fastjson2 -->
<dependency>
    <groupId>com.alibaba.fastjson2</groupId>
    <artifactId>fastjson2</artifactId>
</dependency>
```

#### 文件 2: `MessageJsonUtils.java`

```java
package ai.sagesource.opensagent.infrastructure.agent.memory;

import ai.sagesource.opensagent.core.llm.message.CompletionMessage;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONReader;
import com.alibaba.fastjson2.JSONWriter;

/**
 * CompletionMessage JSON 序列化工具
 * <p>
 * 基于 fastjson2 的 autoType 特性，支持 CompletionMessage 多态子类的完整序列化与反序列化。
 *
 * @author: sage.xue
 * @time: 2026/4/19
 */
public class MessageJsonUtils {

    /**
     * 将 CompletionMessage 序列化为 JSON 字符串
     *
     * @param message 消息对象
     * @return JSON 字符串（包含 @type 类型信息）
     */
    public static String toJson(CompletionMessage message) {
        return JSON.toJSONString(message, JSONWriter.Feature.WriteClassName);
    }

    /**
     * 将 JSON 字符串反序列化为 CompletionMessage
     *
     * @param json JSON 字符串
     * @return CompletionMessage 对象
     */
    public static CompletionMessage fromJson(String json) {
        return JSON.parseObject(json, CompletionMessage.class, JSONReader.Feature.SupportAutoType);
    }
}
```

#### 文件 3: `LLMMemoryCompressionStrategy.java`

```java
package ai.sagesource.opensagent.infrastructure.agent.memory;

import ai.sagesource.opensagent.core.agent.memory.MemoryCompressionStrategy;
import ai.sagesource.opensagent.core.agent.memory.MemoryItem;
import ai.sagesource.opensagent.core.agent.prompt.PromptRenderContext;
import ai.sagesource.opensagent.core.agent.prompt.PromptTemplate;
import ai.sagesource.opensagent.core.llm.completion.CompletionRequest;
import ai.sagesource.opensagent.core.llm.completion.CompletionResponse;
import ai.sagesource.opensagent.core.llm.completion.LLMCompletion;
import ai.sagesource.opensagent.core.llm.message.AssistantCompletionMessage;
import ai.sagesource.opensagent.core.llm.message.CompletionMessage;
import ai.sagesource.opensagent.core.llm.message.SystemCompletionMessage;
import ai.sagesource.opensagent.core.llm.message.UserCompletionMessage;
import ai.sagesource.opensagent.infrastructure.agent.prompt.DefaultPromptTemplate;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 基于 LLM 的智能记忆压缩策略
 * <p>
 * 通过注入 LLMCompletion，调用大模型对历史对话进行语义理解和信息提炼，生成高质量的记忆摘要。
 * <ul>
 *     <li>系统 Prompt 由外部传入的 {@code systemPromptTemplate} 提供，包含压缩指令与角色定义，无内置预设</li>
 *     <li>用户 Prompt 使用内置 {@code DEFAULT_USER_TEMPLATE}，仅包含 {@code {{memoryItems}}}、{@code {{messages}}} 动态内容占位符，不支持外部替换</li>
 * </ul>
 *
 * @author: sage.xue
 * @time: 2026/4/19
 */
@Slf4j
public class LLMMemoryCompressionStrategy implements MemoryCompressionStrategy {

    /**
     * 默认用户 Prompt 模板（内置预设，仅包含动态内容占位符）
     * <p>
     * 压缩指令由外部传入的 {@code systemPromptTemplate} 提供。
     */
    public static final String DEFAULT_USER_TEMPLATE =
            "{{memoryItems}}" +
            "{{messages}}";

    private final LLMCompletion completion;
    private final PromptTemplate systemPromptTemplate;
    private final PromptTemplate userPromptTemplate;

    /**
     * 创建压缩策略
     *
     * @param completion            LLMCompletion 实例
     * @param systemPromptTemplate  系统 Prompt 模板（由外部提供，无内置预设）
     */
    public LLMMemoryCompressionStrategy(LLMCompletion completion, PromptTemplate systemPromptTemplate) {
        this.completion = completion;
        this.systemPromptTemplate = systemPromptTemplate;
        this.userPromptTemplate = new DefaultPromptTemplate(DEFAULT_USER_TEMPLATE);
    }

    @Override
    public String compress(
            List<MemoryItem> memoryItems,
            List<CompletionMessage> uncompressedMessages,
            String lastMemoryItemId,
            String lastMessageId) {

        // 构建 PromptRenderContext
        Map<String, String> variables = new HashMap<>();

        StringBuilder memoryBuilder = new StringBuilder();
        if (memoryItems != null && !memoryItems.isEmpty()) {
            memoryBuilder.append("【已有记忆】\n");
            for (MemoryItem item : memoryItems) {
                memoryBuilder.append(item.getContent()).append("\n");
            }
            memoryBuilder.append("\n");
        }
        variables.put("memoryItems", memoryBuilder.toString());

        StringBuilder messageBuilder = new StringBuilder();
        messageBuilder.append("【待压缩对话】\n");
        for (CompletionMessage msg : uncompressedMessages) {
            messageBuilder.append("[").append(msg.getRole()).append("]: ")
                    .append(msg.getTextContent()).append("\n");
        }
        variables.put("messages", messageBuilder.toString());

        PromptRenderContext context = PromptRenderContext.of(variables);

        String systemPrompt = systemPromptTemplate.render(PromptRenderContext.empty());
        String userPrompt = userPromptTemplate.render(context);

        CompletionRequest request = CompletionRequest.builder()
                .messages(List.of(
                        SystemCompletionMessage.of(systemPrompt),
                        UserCompletionMessage.of(userPrompt)
                ))
                .temperature(0.3)
                .maxTokens(500)
                .build();

        log.info("> MemoryCompression | 调用 LLM 进行记忆压缩, messages: {} <", uncompressedMessages.size());

        CompletionResponse response = completion.complete(request);
        if (response == null || response.getMessage() == null) {
            throw new RuntimeException("LLM 压缩记忆失败：响应为空");
        }

        AssistantCompletionMessage assistantMsg = response.getMessage();
        String compressed = assistantMsg.getTextContent();
        if (compressed == null || compressed.isBlank()) {
            throw new RuntimeException("LLM 压缩记忆失败：返回内容为空");
        }

        log.info("> MemoryCompression | LLM 记忆压缩完成, 结果长度: {} <", compressed.length());
        return compressed.trim();
    }
}
```

#### 文件 4: `MultipleSQLLiteMemory.java`

```java
package ai.sagesource.opensagent.infrastructure.agent.memory;

import ai.sagesource.opensagent.core.agent.memory.CompressionResult;
import ai.sagesource.opensagent.core.agent.memory.Memory;
import ai.sagesource.opensagent.core.agent.memory.MemoryCompressionStrategy;
import ai.sagesource.opensagent.core.agent.memory.MemoryItem;
import ai.sagesource.opensagent.core.llm.completion.LLMCompletion;
import ai.sagesource.opensagent.core.llm.message.CompletionMessage;
import ai.sagesource.opensagent.core.llm.message.MessageRole;
import lombok.extern.slf4j.Slf4j;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 基于 SQLite 的持久化 Memory 实现
 * <p>
 * 特性说明：
 * <ul>
 *     <li>多会话隔离：通过 sessionId 区分不同对话窗口，数据持久化在 SQLite 中</li>
 *     <li>智能压缩：注入 LLMCompletion，通过大模型实现语义级记忆压缩</li>
 *     <li>滑动窗口：保留最近 windowSize 条未压缩消息，超出部分触发压缩</li>
 *     <li>消息过滤：压缩时剔除 SYSTEM 消息，多个 TOOL 消息只保留最新一次</li>
 *     <li>数据恢复：进程重启后通过 sessionId 可恢复完整对话历史和记忆</li>
 * </ul>
 *
 * @author: sage.xue
 * @time: 2026/4/19
 */
@Slf4j
public class MultipleSQLLiteMemory implements Memory {

    /**
     * 默认滑动窗口大小
     */
    private static final int DEFAULT_WINDOW_SIZE = 50;

    /**
     * 默认数据库文件路径
     */
    private static final String DEFAULT_DB_PATH = "memory.db";

    private final String sessionId;
    private final String dbPath;
    private final int windowSize;
    private final LLMCompletion completion;
    private final MemoryCompressionStrategy fallbackStrategy;

    /**
     * 创建 MultipleSQLLiteMemory（使用默认配置）
     *
     * @param sessionId 会话唯一标识
     */
    public MultipleSQLLiteMemory(String sessionId) {
        this(sessionId, DEFAULT_DB_PATH, DEFAULT_WINDOW_SIZE, null, null);
    }

    /**
     * 创建 MultipleSQLLiteMemory（完整配置）
     *
     * @param sessionId          会话唯一标识
     * @param dbPath             SQLite 数据库文件路径
     * @param windowSize         滑动窗口大小
     * @param completion         LLMCompletion（用于智能压缩，可为 null）
     * @param fallbackStrategy   压缩回退策略（completion 为 null 时使用，可为 null）
     */
    public MultipleSQLLiteMemory(String sessionId,
                                  String dbPath,
                                  int windowSize,
                                  LLMCompletion completion,
                                  MemoryCompressionStrategy fallbackStrategy) {
        this.sessionId = sessionId;
        this.dbPath = dbPath != null ? dbPath : DEFAULT_DB_PATH;
        this.windowSize = windowSize > 0 ? windowSize : DEFAULT_WINDOW_SIZE;
        this.completion = completion;
        this.fallbackStrategy = fallbackStrategy;
        initDatabase();
        ensureSession();
    }

    /**
     * 获取数据库连接
     */
    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + dbPath);
    }

    /**
     * 初始化数据库表结构
     */
    private void initDatabase() {
        String sqlSessions = "CREATE TABLE IF NOT EXISTS sessions (" +
                "session_id TEXT PRIMARY KEY, " +
                "window_size INTEGER DEFAULT 50, " +
                "created_at BIGINT, " +
                "updated_at BIGINT)";

        String sqlMessages = "CREATE TABLE IF NOT EXISTS messages (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "session_id TEXT NOT NULL, " +
                "message_id TEXT, " +
                "role TEXT NOT NULL, " +
                "content_json TEXT NOT NULL, " +
                "sequence INTEGER NOT NULL, " +
                "is_uncompressed INTEGER DEFAULT 1, " +
                "created_at BIGINT)";

        String sqlMemoryItems = "CREATE TABLE IF NOT EXISTS memory_items (" +
                "memory_item_id TEXT PRIMARY KEY, " +
                "session_id TEXT NOT NULL, " +
                "content TEXT NOT NULL, " +
                "last_message_id TEXT, " +
                "last_memory_item_id TEXT, " +
                "timestamp BIGINT)";

        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute(sqlSessions);
            stmt.execute(sqlMessages);
            stmt.execute(sqlMemoryItems);
            log.debug("> Memory | SQLite 数据库初始化完成, dbPath: {} <", dbPath);
        } catch (SQLException e) {
            throw new RuntimeException("初始化 SQLite 数据库失败: " + dbPath, e);
        }
    }

    /**
     * 确保当前 session 记录存在于 sessions 表
     */
    private void ensureSession() {
        String sql = "INSERT OR IGNORE INTO sessions (session_id, window_size, created_at, updated_at) VALUES (?, ?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, sessionId);
            ps.setInt(2, windowSize);
            ps.setLong(3, System.currentTimeMillis());
            ps.setLong(4, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("创建会话记录失败, sessionId: " + sessionId, e);
        }
    }

    @Override
    public void addMessage(CompletionMessage message) {
        if (message == null) {
            return;
        }
        String sql = "INSERT INTO messages (session_id, message_id, role, content_json, sequence, is_uncompressed, created_at) " +
                "VALUES (?, ?, ?, ?, ?, 1, ?)";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            int nextSeq = getNextSequence(conn);
            ps.setString(1, sessionId);
            ps.setString(2, message.getMessageId());
            ps.setString(3, message.getRole().name());
            ps.setString(4, MessageJsonUtils.toJson(message));
            ps.setInt(5, nextSeq);
            ps.setLong(6, System.currentTimeMillis());
            ps.executeUpdate();
            log.info("> Memory | 添加消息成功, session: {}, role: {} <", sessionId, message.getRole());
        } catch (SQLException e) {
            throw new RuntimeException("添加消息失败, sessionId: " + sessionId, e);
        }
    }

    /**
     * 获取当前会话下一条消息的 sequence 值
     */
    private int getNextSequence(Connection conn) throws SQLException {
        String sql = "SELECT COALESCE(MAX(sequence), 0) + 1 FROM messages WHERE session_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, sessionId);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getInt(1) : 1;
        }
    }

    @Override
    public void addMessages(List<CompletionMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return;
        }
        for (CompletionMessage message : messages) {
            addMessage(message);
        }
    }

    @Override
    public List<CompletionMessage> getMessages() {
        String sql = "SELECT content_json FROM messages WHERE session_id = ? ORDER BY sequence";
        List<CompletionMessage> result = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, sessionId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                result.add(MessageJsonUtils.fromJson(rs.getString("content_json")));
            }
        } catch (SQLException e) {
            throw new RuntimeException("查询消息失败, sessionId: " + sessionId, e);
        }
        return result;
    }

    @Override
    public List<CompletionMessage> getUncompressedMessages() {
        String sql = "SELECT content_json FROM messages WHERE session_id = ? AND is_uncompressed = 1 ORDER BY sequence";
        List<CompletionMessage> result = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, sessionId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                result.add(MessageJsonUtils.fromJson(rs.getString("content_json")));
            }
        } catch (SQLException e) {
            throw new RuntimeException("查询未压缩消息失败, sessionId: " + sessionId, e);
        }
        return result;
    }

    @Override
    public List<MemoryItem> getMemoryItems() {
        String sql = "SELECT memory_item_id, content, last_message_id, last_memory_item_id, timestamp " +
                "FROM memory_items WHERE session_id = ? ORDER BY timestamp";
        List<MemoryItem> result = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, sessionId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                result.add(MemoryItem.builder()
                        .memoryItemId(rs.getString("memory_item_id"))
                        .content(rs.getString("content"))
                        .lastMessageId(rs.getString("last_message_id"))
                        .lastMemoryItemId(rs.getString("last_memory_item_id"))
                        .timestamp(rs.getLong("timestamp"))
                        .build());
            }
        } catch (SQLException e) {
            throw new RuntimeException("查询记忆项失败, sessionId: " + sessionId, e);
        }
        return result;
    }

    @Override
    public CompressionResult compress() {
        List<CompletionMessage> uncompressed = getUncompressedMessages();
        if (uncompressed.isEmpty()) {
            log.warn("> Memory | session: {} 无可压缩的对话历史 <", sessionId);
            return CompressionResult.skipped("无可压缩的对话历史");
        }

        int compressCount = Math.max(0, uncompressed.size() - windowSize);
        if (compressCount == 0) {
            log.warn("> Memory | session: {} 未压缩消息数量未超过滑动窗口阈值 {}，无需压缩 <", sessionId, windowSize);
            return CompressionResult.skipped("未压缩消息数量未超过滑动窗口阈值，无需压缩");
        }

        List<CompletionMessage> toCompress = new ArrayList<>(uncompressed.subList(0, compressCount));

        // 找出最后一个 TOOL 消息的索引
        int lastToolIndex = -1;
        for (int i = 0; i < toCompress.size(); i++) {
            if (toCompress.get(i).getRole() == MessageRole.TOOL) {
                lastToolIndex = i;
            }
        }

        // 过滤 SYSTEM 消息和多余的 TOOL 消息
        List<CompletionMessage> filtered = new ArrayList<>();
        String lastMsgId = null;
        for (int i = 0; i < toCompress.size(); i++) {
            CompletionMessage msg = toCompress.get(i);
            if (msg.getRole() == MessageRole.SYSTEM) {
                continue;
            }
            if (msg.getRole() == MessageRole.TOOL && i != lastToolIndex) {
                continue;
            }
            filtered.add(msg);
            if (msg.getMessageId() != null) {
                lastMsgId = msg.getMessageId();
            }
        }

        // 如果过滤后没有有效内容，直接标记已压缩并返回跳过
        if (filtered.isEmpty()) {
            markCompressed(compressCount);
            log.info("> Memory | session: {} 过滤后无可压缩的有效对话历史 <", sessionId);
            return CompressionResult.skipped("过滤后无可压缩的有效对话历史");
        }

        // 获取已有记忆和最后记忆项 ID
        List<MemoryItem> existingItems = getMemoryItems();
        String lastMemoryItemId = existingItems.isEmpty()
                ? null
                : existingItems.get(existingItems.size() - 1).getMemoryItemId();

        // 执行压缩
        String compressedContent;
        if (completion != null) {
            MemoryCompressionStrategy strategy = fallbackStrategy != null
                    ? fallbackStrategy
                    : new LLMMemoryCompressionStrategy(completion);
            compressedContent = strategy.compress(existingItems, filtered, lastMemoryItemId, lastMsgId);
        } else {
            // 本地回退压缩（类似 SimpleMemory 的拼接逻辑）
            StringBuilder sb = new StringBuilder();
            for (CompletionMessage msg : filtered) {
                sb.append("[").append(msg.getRole()).append("]: ")
                        .append(msg.getTextContent()).append("\n");
            }
            compressedContent = sb.toString().trim();
        }

        MemoryItem item = MemoryItem.builder()
                .memoryItemId(UUID.randomUUID().toString())
                .content(compressedContent)
                .lastMessageId(lastMsgId)
                .lastMemoryItemId(lastMemoryItemId)
                .timestamp(System.currentTimeMillis())
                .build();

        saveMemoryItem(item);
        markCompressed(compressCount);
        updateSessionTime();

        log.info("> Memory | session: {} 压缩完成, memoryItemId: {}, 压缩消息数: {}, 保留消息数: {} <",
                sessionId, item.getMemoryItemId(), compressCount, uncompressed.size() - compressCount);
        return CompressionResult.success(item);
    }

    /**
     * 将指定数量的最早未压缩消息标记为已压缩
     */
    private void markCompressed(int count) {
        String sql = "UPDATE messages SET is_uncompressed = 0 WHERE id IN (" +
                "SELECT id FROM messages WHERE session_id = ? AND is_uncompressed = 1 ORDER BY sequence LIMIT ?)";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, sessionId);
            ps.setInt(2, count);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("标记消息已压缩失败, sessionId: " + sessionId, e);
        }
    }

    /**
     * 保存记忆项到数据库
     */
    private void saveMemoryItem(MemoryItem item) {
        String sql = "INSERT INTO memory_items (memory_item_id, session_id, content, last_message_id, last_memory_item_id, timestamp) " +
                "VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, item.getMemoryItemId());
            ps.setString(2, sessionId);
            ps.setString(3, item.getContent());
            ps.setString(4, item.getLastMessageId());
            ps.setString(5, item.getLastMemoryItemId());
            ps.setLong(6, item.getTimestamp());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("保存记忆项失败, sessionId: " + sessionId, e);
        }
    }

    /**
     * 更新会话更新时间
     */
    private void updateSessionTime() {
        String sql = "UPDATE sessions SET updated_at = ? WHERE session_id = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, System.currentTimeMillis());
            ps.setString(2, sessionId);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.warn("> Memory | 更新会话时间失败, sessionId: {} <", sessionId, e);
        }
    }

    @Override
    public void clear() {
        String sqlMsg = "DELETE FROM messages WHERE session_id = ?";
        String sqlMem = "DELETE FROM memory_items WHERE session_id = ?";
        try (Connection conn = getConnection();
             PreparedStatement psMsg = conn.prepareStatement(sqlMsg);
             PreparedStatement psMem = conn.prepareStatement(sqlMem)) {
            psMsg.setString(1, sessionId);
            psMsg.executeUpdate();
            psMem.setString(1, sessionId);
            psMem.executeUpdate();
            log.info("> Memory | 清空 session: {} 的所有记忆 <", sessionId);
        } catch (SQLException e) {
            throw new RuntimeException("清空记忆失败, sessionId: " + sessionId, e);
        }
    }

    @Override
    public String getLastMessageId() {
        String sql = "SELECT message_id FROM messages WHERE session_id = ? ORDER BY sequence DESC LIMIT 1";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, sessionId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getString("message_id");
            }
        } catch (SQLException e) {
            throw new RuntimeException("获取最后消息 ID 失败, sessionId: " + sessionId, e);
        }
        return null;
    }

    @Override
    public String getLastMemoryItemId() {
        String sql = "SELECT memory_item_id FROM memory_items WHERE session_id = ? ORDER BY timestamp DESC LIMIT 1";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, sessionId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getString("memory_item_id");
            }
        } catch (SQLException e) {
            throw new RuntimeException("获取最后记忆项 ID 失败, sessionId: " + sessionId, e);
        }
        return null;
    }
}
```

#### 文件 5: `MessageJsonUtilsTest.java`

```java
package ai.sagesource.opensagent.infrastructure.agent.memory;

import ai.sagesource.opensagent.core.llm.message.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MessageJsonUtils 单元测试
 *
 * @author: sage.xue
 * @time: 2026/4/19
 */
class MessageJsonUtilsTest {

    @Test
    @DisplayName("序列化与反序列化 UserCompletionMessage - 成功")
    void testSerializeDeserializeUserMessage() {
        UserCompletionMessage msg = UserCompletionMessage.builder()
                .messageId("msg-001")
                .contents(new java.util.ArrayList<>(java.util.List.of(
                        TextContent.builder().text("你好").build())))
                .build();

        String json = MessageJsonUtils.toJson(msg);
        assertNotNull(json);
        assertTrue(json.contains("UserCompletionMessage"));

        CompletionMessage deserialized = MessageJsonUtils.fromJson(json);
        assertTrue(deserialized instanceof UserCompletionMessage);
        assertEquals("msg-001", deserialized.getMessageId());
        assertEquals("你好", deserialized.getTextContent());
        assertEquals(MessageRole.USER, deserialized.getRole());
    }

    @Test
    @DisplayName("序列化与反序列化 AssistantCompletionMessage - 成功")
    void testSerializeDeserializeAssistantMessage() {
        AssistantCompletionMessage msg = AssistantCompletionMessage.builder()
                .messageId("msg-002")
                .contents(new java.util.ArrayList<>(java.util.List.of(
                        TextContent.builder().text("你好，有什么可以帮你的？").build())))
                .build();

        String json = MessageJsonUtils.toJson(msg);
        CompletionMessage deserialized = MessageJsonUtils.fromJson(json);
        assertTrue(deserialized instanceof AssistantCompletionMessage);
        assertEquals("msg-002", deserialized.getMessageId());
        assertEquals("你好，有什么可以帮你的？", deserialized.getTextContent());
    }

    @Test
    @DisplayName("序列化与反序列化 ToolCompletionMessage - 成功")
    void testSerializeDeserializeToolMessage() {
        ToolCompletionMessage msg = ToolCompletionMessage.of("call-1", "weather_tool", "晴天");

        String json = MessageJsonUtils.toJson(msg);
        CompletionMessage deserialized = MessageJsonUtils.fromJson(json);
        assertTrue(deserialized instanceof ToolCompletionMessage);
        assertEquals("晴天", deserialized.getTextContent());
        assertEquals(MessageRole.TOOL, deserialized.getRole());
    }
}
```

#### 文件 6: `LLMMemoryCompressionStrategyTest.java`

```java
package ai.sagesource.opensagent.infrastructure.agent.memory;

import ai.sagesource.opensagent.core.agent.memory.MemoryItem;
import ai.sagesource.opensagent.core.agent.prompt.PromptRenderContext;
import ai.sagesource.opensagent.core.agent.prompt.PromptTemplate;
import ai.sagesource.opensagent.core.llm.completion.CompletionRequest;
import ai.sagesource.opensagent.core.llm.completion.CompletionResponse;
import ai.sagesource.opensagent.core.llm.completion.LLMCompletion;
import ai.sagesource.opensagent.core.llm.message.AssistantCompletionMessage;
import ai.sagesource.opensagent.core.llm.message.UserCompletionMessage;
import ai.sagesource.opensagent.infrastructure.agent.prompt.DefaultPromptTemplate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * LLMMemoryCompressionStrategy 单元测试
 *
 * @author: sage.xue
 * @time: 2026/4/19
 */
class LLMMemoryCompressionStrategyTest {

    @Test
    @DisplayName("内置用户模板 + 外部系统模板压缩 - 成功")
    void testCompressWithSystemTemplate() {
        PromptTemplate systemTemplate = new DefaultPromptTemplate("你是一个记忆压缩专家");

        LLMCompletion mockCompletion = request -> {
            assertNotNull(request);
            assertEquals(2, request.getMessages().size());
            // 系统 Prompt 由外部传入
            assertTrue(request.getMessages().get(0).getTextContent().contains("记忆压缩专家"));
            // 用户 Prompt 仅包含动态内容占位符渲染结果
            String userPrompt = request.getMessages().get(1).getTextContent();
            assertTrue(userPrompt.contains("【待压缩对话】"));
            assertFalse(userPrompt.contains("记忆压缩助手")); // 压缩指令在系统 Prompt 中，不在用户 Prompt

            return CompletionResponse.builder()
                    .message(AssistantCompletionMessage.of("用户询问了天气，助手回答今天是晴天。"))
                    .build();
        };

        LLMMemoryCompressionStrategy strategy = new LLMMemoryCompressionStrategy(mockCompletion, systemTemplate);

        List<MemoryItem> memoryItems = new ArrayList<>();
        List<UserCompletionMessage> messages = new ArrayList<>();
        messages.add(UserCompletionMessage.of("今天天气怎么样？"));

        String result = strategy.compress(memoryItems, new ArrayList<>(messages), null, "msg-001");

        assertNotNull(result);
        assertEquals("用户询问了天气，助手回答今天是晴天。", result);
    }

    @Test
    @DisplayName("PromptTemplate 占位符渲染 - 正确替换")
    void testPromptTemplateRendering() {
        DefaultPromptTemplate template = new DefaultPromptTemplate("记忆：{{memoryItems}}，对话：{{messages}}");
        PromptRenderContext ctx = PromptRenderContext.builder()
                .variables(new java.util.HashMap<>() {{
                    put("memoryItems", "已有记忆");
                    put("messages", "待压缩对话");
                }})
                .build();

        String rendered = template.render(ctx);
        assertEquals("记忆：已有记忆，对话：待压缩对话", rendered);
    }

    @Test
    @DisplayName("LLM 响应为空 - 抛出异常")
    void testCompressWhenResponseEmpty() {
        LLMCompletion mockCompletion = request -> CompletionResponse.builder().build();
        PromptTemplate systemTemplate = new DefaultPromptTemplate("系统提示");

        LLMMemoryCompressionStrategy strategy = new LLMMemoryCompressionStrategy(mockCompletion, systemTemplate);
        assertThrows(RuntimeException.class, () ->
                strategy.compress(new ArrayList<>(), new ArrayList<>(), null, null));
    }
}
```

#### 文件 7: `MultipleSQLLiteMemoryTest.java`

```java
package ai.sagesource.opensagent.infrastructure.agent.memory;

import ai.sagesource.opensagent.core.agent.memory.CompressionResult;
import ai.sagesource.opensagent.core.agent.memory.MemoryItem;
import ai.sagesource.opensagent.core.llm.completion.CompletionResponse;
import ai.sagesource.opensagent.core.llm.message.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MultipleSQLLiteMemory 单元测试
 * <p>
 * 每个测试使用独立的 sessionId 和临时数据库文件，确保测试隔离。
 *
 * @author: sage.xue
 * @time: 2026/4/19
 */
class MultipleSQLLiteMemoryTest {

    private static final String TEST_DB = "test-memory.db";

    @BeforeEach
    @AfterEach
    void cleanUp() {
        File dbFile = new File(TEST_DB);
        if (dbFile.exists()) {
            dbFile.delete();
        }
    }

    private MultipleSQLLiteMemory createMemory() {
        return new MultipleSQLLiteMemory(UUID.randomUUID().toString(), TEST_DB, 3, null, null);
    }

    @Test
    @DisplayName("添加单条消息并查询 - 成功")
    void testAddAndGetMessage() {
        MultipleSQLLiteMemory memory = createMemory();
        memory.addMessage(UserCompletionMessage.of("你好"));

        assertEquals(1, memory.getMessages().size());
        assertEquals(1, memory.getUncompressedMessages().size());
        assertEquals("你好", memory.getMessages().get(0).getTextContent());
    }

    @Test
    @DisplayName("批量添加消息 - 成功")
    void testAddMessages() {
        MultipleSQLLiteMemory memory = createMemory();
        List<CompletionMessage> messages = Arrays.asList(
                UserCompletionMessage.of("你好"),
                AssistantCompletionMessage.of("你好，有什么可以帮你的？")
        );

        memory.addMessages(messages);

        assertEquals(2, memory.getMessages().size());
        assertEquals(2, memory.getUncompressedMessages().size());
    }

    @Test
    @DisplayName("滑动窗口压缩 - 保留窗口内消息")
    void testCompressWithSlidingWindow() {
        MultipleSQLLiteMemory memory = createMemory();
        memory.addMessage(UserCompletionMessage.of("msg1"));
        memory.addMessage(UserCompletionMessage.of("msg2"));
        memory.addMessage(UserCompletionMessage.of("msg3"));
        memory.addMessage(UserCompletionMessage.of("msg4"));
        memory.addMessage(UserCompletionMessage.of("msg5"));

        CompressionResult result = memory.compress();

        assertTrue(result.isSuccess());
        assertEquals(3, memory.getUncompressedMessages().size());
        assertEquals(5, memory.getMessages().size());
        assertEquals(1, memory.getMemoryItems().size());
    }

    @Test
    @DisplayName("压缩时未超过窗口阈值 - 返回跳过")
    void testCompressWhenBelowThreshold() {
        MultipleSQLLiteMemory memory = new MultipleSQLLiteMemory(
                UUID.randomUUID().toString(), TEST_DB, 10, null, null);
        memory.addMessage(UserCompletionMessage.of("msg1"));
        memory.addMessage(UserCompletionMessage.of("msg2"));

        CompressionResult result = memory.compress();

        assertFalse(result.isSuccess());
        assertEquals("未压缩消息数量未超过滑动窗口阈值，无需压缩", result.getMessage());
    }

    @Test
    @DisplayName("压缩时剔除 SYSTEM 消息 - 成功")
    void testCompressFiltersSystemMessages() {
        MultipleSQLLiteMemory memory = new MultipleSQLLiteMemory(
                UUID.randomUUID().toString(), TEST_DB, 1, null, null);
        memory.addMessage(SystemCompletionMessage.of("系统提示"));
        memory.addMessage(UserCompletionMessage.of("你好"));

        CompressionResult result = memory.compress();

        assertTrue(result.isSuccess());
        assertNotNull(result.getMemoryItem());
        assertFalse(result.getMemoryItem().getContent().contains("SYSTEM"));
        assertTrue(result.getMemoryItem().getContent().contains("USER"));
    }

    @Test
    @DisplayName("压缩时 TOOL 消息只保留最新一次 - 成功")
    void testCompressKeepsLatestToolMessageOnly() {
        MultipleSQLLiteMemory memory = new MultipleSQLLiteMemory(
                UUID.randomUUID().toString(), TEST_DB, 1, null, null);
        memory.addMessage(ToolCompletionMessage.of("call-1", "tool1", "结果1"));
        memory.addMessage(UserCompletionMessage.of("谢谢"));
        memory.addMessage(ToolCompletionMessage.of("call-2", "tool2", "结果2"));

        CompressionResult result = memory.compress();

        assertTrue(result.isSuccess());
        String content = result.getMemoryItem().getContent();
        assertEquals(1, countOccurrences(content, "TOOL"));
        assertTrue(content.contains("结果2"));
        assertFalse(content.contains("结果1"));
    }

    @Test
    @DisplayName("清空记忆 - 成功")
    void testClear() {
        MultipleSQLLiteMemory memory = createMemory();
        memory.addMessage(UserCompletionMessage.of("你好"));
        memory.compress();

        memory.clear();

        assertTrue(memory.getMessages().isEmpty());
        assertTrue(memory.getUncompressedMessages().isEmpty());
        assertTrue(memory.getMemoryItems().isEmpty());
    }

    @Test
    @DisplayName("多会话隔离 - 成功")
    void testSessionIsolation() {
        String session1 = "session-1";
        String session2 = "session-2";

        MultipleSQLLiteMemory memory1 = new MultipleSQLLiteMemory(session1, TEST_DB, 3, null, null);
        MultipleSQLLiteMemory memory2 = new MultipleSQLLiteMemory(session2, TEST_DB, 3, null, null);

        memory1.addMessage(UserCompletionMessage.of("会话1的消息"));
        memory2.addMessage(UserCompletionMessage.of("会话2的消息"));

        assertEquals(1, memory1.getMessages().size());
        assertEquals(1, memory2.getMessages().size());
        assertEquals("会话1的消息", memory1.getMessages().get(0).getTextContent());
        assertEquals("会话2的消息", memory2.getMessages().get(0).getTextContent());
    }

    @Test
    @DisplayName("持久化恢复 - 成功")
    void testPersistenceRecovery() {
        String sessionId = UUID.randomUUID().toString();

        // 第一个实例写入数据
        MultipleSQLLiteMemory memory1 = new MultipleSQLLiteMemory(sessionId, TEST_DB, 2, null, null);
        memory1.addMessage(UserCompletionMessage.of("msg1"));
        memory1.addMessage(UserCompletionMessage.of("msg2"));
        memory1.addMessage(UserCompletionMessage.of("msg3"));
        memory1.compress();

        // 第二个实例读取数据
        MultipleSQLLiteMemory memory2 = new MultipleSQLLiteMemory(sessionId, TEST_DB, 2, null, null);

        assertEquals(3, memory2.getMessages().size());
        assertEquals(2, memory2.getUncompressedMessages().size());
        assertEquals(1, memory2.getMemoryItems().size());
    }

    @Test
    @DisplayName("LLM 智能压缩 - 成功")
    void testLLMCompression() {
        // Mock LLMCompletion
        ai.sagesource.opensagent.core.llm.completion.LLMCompletion mockCompletion = request -> {
            return CompletionResponse.builder()
                    .message(AssistantCompletionMessage.of("这是 LLM 压缩后的记忆摘要。"))
                    .build();
        };

        MultipleSQLLiteMemory memory = new MultipleSQLLiteMemory(
                UUID.randomUUID().toString(), TEST_DB, 1, mockCompletion, null);
        memory.addMessage(UserCompletionMessage.of("请帮我总结这段对话。"));
        memory.addMessage(AssistantCompletionMessage.of("好的，这段对话的核心是..."));

        CompressionResult result = memory.compress();

        assertTrue(result.isSuccess());
        assertEquals("这是 LLM 压缩后的记忆摘要。", result.getMemoryItem().getContent());
    }

    @Test
    @DisplayName("获取最后消息 ID - 成功")
    void testGetLastMessageId() {
        MultipleSQLLiteMemory memory = createMemory();
        UserCompletionMessage msg = UserCompletionMessage.builder()
                .messageId("msg-last")
                .contents(new java.util.ArrayList<>(java.util.List.of(
                        TextContent.builder().text("你好").build())))
                .build();
        memory.addMessage(msg);

        assertEquals("msg-last", memory.getLastMessageId());
    }

    @Test
    @DisplayName("获取最后记忆项 ID - 成功")
    void testGetLastMemoryItemId() {
        MultipleSQLLiteMemory memory = createMemory();
        memory.addMessage(UserCompletionMessage.of("你好"));
        CompressionResult result = memory.compress();

        assertNotNull(memory.getLastMemoryItemId());
        assertEquals(result.getMemoryItem().getMemoryItemId(), memory.getLastMemoryItemId());
    }

    @Test
    @DisplayName("压缩结果包含关联 ID - 成功")
    void testCompressionResultContainsRelationIds() {
        MultipleSQLLiteMemory memory = createMemory();
        UserCompletionMessage msg1 = UserCompletionMessage.builder()
                .messageId("msg-001")
                .contents(new java.util.ArrayList<>(java.util.List.of(
                        TextContent.builder().text("你好").build())))
                .build();
        memory.addMessage(msg1);
        CompressionResult first = memory.compress();
        assertNotNull(first.getMemoryItem());
        assertEquals("msg-001", first.getMemoryItem().getLastMessageId());
        assertNull(first.getMemoryItem().getLastMemoryItemId());

        UserCompletionMessage msg2 = UserCompletionMessage.builder()
                .messageId("msg-002")
                .contents(new java.util.ArrayList<>(java.util.List.of(
                        TextContent.builder().text("再见").build())))
                .build();
        memory.addMessage(msg2);
        CompressionResult second = memory.compress();
        assertNotNull(second.getMemoryItem());
        assertEquals("msg-002", second.getMemoryItem().getLastMessageId());
        assertEquals(first.getMemoryItem().getMemoryItemId(), second.getMemoryItem().getLastMemoryItemId());
    }

    private int countOccurrences(String text, String sub) {
        int count = 0;
        int idx = 0;
        while ((idx = text.indexOf(sub, idx)) != -1) {
            count++;
            idx += sub.length();
        }
        return count;
    }
}
```

### 2.4 Prompt 模板设计说明

`LLMMemoryCompressionStrategy` 使用 **Prompt 模块**（`PromptTemplate` + `PromptRenderContext`）管理 Prompt，支持占位符动态渲染。

**设计原则：**

| 模板类型 | 管理方式 | 职责 | 说明 |
|----------|----------|------|------|
| 系统 Prompt | **外部传入**（`systemPromptTemplate`） | 定义压缩规则、角色身份、行为约束 | 无内置预设，由调用方通过构造函数传入，保持与上层 Agent 的 Prompt 体系一致 |
| 用户 Prompt | **内置预设**（`DEFAULT_USER_TEMPLATE`） | 注入动态待压缩内容 | 类内部常量，不从外部传入，仅包含 `{{memoryItems}}`、`{{messages}}` 占位符 |

**占位符作用：**

| 占位符 | 注入内容 | 说明 |
|--------|----------|------|
| `{{memoryItems}}` | 已有记忆历史文本（含【已有记忆】前缀） | 为模型提供历史压缩记忆上下文 |
| `{{messages}}` | 待压缩对话文本（含【待压缩对话】前缀） | 为模型提供本次需要压缩的原始对话 |

**默认用户模板结构：**

```
{{memoryItems}}
{{messages}}
```

**渲染流程：**

```
1. 构建 PromptRenderContext
   - memoryItems: 已有记忆历史文本
   - messages: 待压缩对话文本

2. 渲染 PromptTemplate
   - systemPrompt = systemPromptTemplate.render(PromptRenderContext.empty())
   - userPrompt = userPromptTemplate.render(context)

3. 组装 CompletionRequest
   - SystemCompletionMessage(systemPrompt)  ← 压缩指令、角色定义
   - UserCompletionMessage(userPrompt)      ← 动态待压缩内容
```

**调用参数：**

| 参数 | 值 | 说明 |
|------|-----|------|
| temperature | 0.3 | 低温度确保输出稳定、可复现 |
| maxTokens | 500 | 控制压缩结果长度，预留安全余量 |

**系统 Prompt 示例：**

以下是一个完整的系统 Prompt 模板示例，供调用方参考：

```
你是一个专业的对话记忆压缩助手。你的任务是对历史对话进行精炼总结，保留关键信息的同时大幅缩减篇幅。

压缩要求：
1. 保留用户的核心需求、关键问题和重要背景信息；
2. 保留助手提供的重要结论、事实、数据和决策依据；
3. 合并重复或相似的内容，避免信息冗余；
4. 去除寒暄、过渡性语句、格式化标记等无关信息；
5. 使用第三人称客观陈述，按时间顺序组织事件；
6. 如果涉及多轮工具调用，保留工具名称和最终有效结果；
7. 总字数严格控制在 300 字以内。

请直接输出压缩后的记忆文本，不要添加任何解释、前缀、后缀或 markdown 格式。
```

**使用方式：**

```java
// 由外部提供系统 Prompt 模板（包含压缩指令）
PromptTemplate systemTemplate = new DefaultPromptTemplate(
    "你是一个专业的对话记忆压缩助手...\n" +
    "压缩要求：...\n" +
    "请直接输出压缩后的记忆文本。"
);
LLMMemoryCompressionStrategy strategy = new LLMMemoryCompressionStrategy(completion, systemTemplate);
```

## 3. 影响范围分析

### 3.1 模块依赖关系

```
open-sagent-core (抽象定义层，无变更)
    ├── Memory, MemoryItem, CompressionResult, MemoryCompressionStrategy
    └── CompletionMessage (多态序列化依赖 fastjson2 autoType)

open-sagent-infrastructure (具体实现层)
    ├── 依赖 open-sagent-core
    ├── 依赖 sqlite-jdbc (新增)
    ├── 依赖 fastjson2 (新增显式依赖)
    ├── MultipleSQLLiteMemory
    ├── LLMMemoryCompressionStrategy
    └── MessageJsonUtils
```

### 3.2 影响范围

| 模块 | 影响说明 |
|------|----------|
| open-sagent-core | **无变更**，Memory 抽象层保持稳定 |
| open-sagent-infrastructure | 新增 3 个实现类、3 个测试类，pom.xml 新增 2 个依赖 |
| open-sagent-infrastructure-openai | 无直接影响 |
| open-sagent-tools | 无直接影响 |
| open-sagent-web | 无直接影响 |
| open-sagent-cli | 无直接影响 |
| open-sagent-example | 可作为使用范例接入 |

### 3.3 扩展性说明

1. **多会话隔离**：通过 `sessionId` 实现会话级别的数据隔离，同一数据库文件可承载多个会话，应用层可按需管理会话生命周期。
2. **Prompt 模块集成**：`LLMMemoryCompressionStrategy` 使用 `PromptTemplate` 管理 Prompt。系统 Prompt 由外部传入，负责压缩指令与角色定义，可与上层 Agent 的 Prompt 体系保持一致；用户 Prompt 内置 `DEFAULT_USER_TEMPLATE`，仅包含 `{{memoryItems}}` 和 `{{messages}}` 占位符，通过 `PromptRenderContext` 注入动态待压缩内容。
3. **压缩策略可替换**：`LLMMemoryCompressionStrategy` 提供默认 LLM 压缩能力，也支持外部传入自定义 `MemoryCompressionStrategy` 实现。
4. **LLM 注入灵活**：`MultipleSQLLiteMemory` 的 `completion` 参数可为 `null`，此时退化为本地规则压缩，便于测试和无 LLM 环境使用。
5. **存储可扩展**：当前基于 SQLite，表结构为标准关系型设计，后续如需迁移至 MySQL/PostgreSQL 等，仅需替换 JDBC 连接层。
6. **消息序列化兼容**：`MessageJsonUtils` 基于 fastjson2 autoType，天然支持 `CompletionMessage` 所有子类的扩展，新增消息类型无需修改序列化逻辑。

## 4. 测试计划

### 4.1 单元测试

| 测试类 | 模块 | 测试内容 |
|--------|------|----------|
| `MessageJsonUtilsTest` | infrastructure | User/Assistant/Tool 消息的序列化与反序列化，多态类型还原验证 |
| `LLMMemoryCompressionStrategyTest` | infrastructure | 默认 Prompt 压缩、自定义 Prompt 压缩、LLM 响应为空时的异常处理 |
| `MultipleSQLLiteMemoryTest` | infrastructure | 消息添加、批量添加、滑动窗口压缩、SYSTEM 过滤、TOOL 去重、清空、关联 ID 验证、多会话隔离、持久化恢复、LLM 智能压缩 |

### 4.2 编译验证

```bash
mvn clean compile test-compile -pl open-sagent-infrastructure -am
```

## 5. 方案变更记录

### 变更 1（2026-04-19）：Prompt 改用 Prompt 模块管理

**变更原因：**
评审意见要求 Prompt 必须使用 Prompt 模块（`PromptTemplate` + `PromptRenderContext`）进行管理，不得硬编码字符串拼接。

**文件变更：**

| 文件路径 | 变更类型 | 说明 |
|----------|----------|------|
| `.agents/plan/011-Agent模块-MultipleSQLLiteMemory设计方案.md` | 修改 | LLMMemoryCompressionStrategy 改用 PromptTemplate 管理 Prompt；更新测试类和 Prompt 说明章节 |

**关键代码变更：**

```java
// 修改前：硬编码字符串
public class LLMMemoryCompressionStrategy implements MemoryCompressionStrategy {
    private final String systemPrompt;
    
    public LLMMemoryCompressionStrategy(LLMCompletion completion, String systemPrompt) {
        this.systemPrompt = systemPrompt;
    }
    
    public String compress(...) {
        StringBuilder userPrompt = new StringBuilder();
        // 直接拼接字符串
        ...
        CompletionRequest request = CompletionRequest.builder()
                .messages(List.of(
                        SystemCompletionMessage.of(systemPrompt),
                        UserCompletionMessage.of(userPrompt.toString().trim())
                ))
                .build();
    }
}

// 修改后：使用 PromptTemplate + PromptRenderContext
public class LLMMemoryCompressionStrategy implements MemoryCompressionStrategy {
    private final PromptTemplate systemPromptTemplate;
    private final PromptTemplate userPromptTemplate;
    
    public LLMMemoryCompressionStrategy(LLMCompletion completion,
                                         PromptTemplate systemPromptTemplate,
                                         PromptTemplate userPromptTemplate) {
        this.systemPromptTemplate = systemPromptTemplate;
        this.userPromptTemplate = userPromptTemplate;
    }
    
    public String compress(...) {
        Map<String, String> variables = new HashMap<>();
        variables.put("memoryItems", ...);
        variables.put("messages", ...);
        
        PromptRenderContext context = PromptRenderContext.of(variables);
        String systemPrompt = systemPromptTemplate.render(PromptRenderContext.empty());
        String userPrompt = userPromptTemplate.render(context);
        
        CompletionRequest request = CompletionRequest.builder()
                .messages(List.of(
                        SystemCompletionMessage.of(systemPrompt),
                        UserCompletionMessage.of(userPrompt)
                ))
                .build();
    }
}
```

### 变更 2（2026-04-19）：系统 Prompt 由外部传入，用户模板内置预设

**变更原因：**
评审意见：1）SYSTEM_PROMPT 无需预设；2）USER_TEMPLATE 无需从外部指定，使用预设模板即可。

**文件变更：**

| 文件路径 | 变更类型 | 说明 |
|----------|----------|------|
| `.agents/plan/011-Agent模块-MultipleSQLLiteMemory设计方案.md` | 修改 | LLMMemoryCompressionStrategy 去掉 DEFAULT_SYSTEM_TEMPLATE 常量；systemPromptTemplate 由外部传入；DEFAULT_USER_TEMPLATE 内置完整压缩指令且不支持外部替换；更新测试类 |

**关键代码变更：**

```java
// 修改前：包含 DEFAULT_SYSTEM_TEMPLATE 预设，支持外部传入用户模板
public class LLMMemoryCompressionStrategy implements MemoryCompressionStrategy {
    public static final String DEFAULT_SYSTEM_TEMPLATE = "...";
    public static final String DEFAULT_USER_TEMPLATE = "{{memoryItems}}{{messages}}";
    
    private final PromptTemplate systemPromptTemplate;
    private final PromptTemplate userPromptTemplate;
    
    public LLMMemoryCompressionStrategy(LLMCompletion completion) {
        this(completion, 
             new DefaultPromptTemplate(DEFAULT_SYSTEM_TEMPLATE),
             new DefaultPromptTemplate(DEFAULT_USER_TEMPLATE));
    }
    
    public LLMMemoryCompressionStrategy(LLMCompletion completion,
                                         PromptTemplate systemPromptTemplate,
                                         PromptTemplate userPromptTemplate) {
        this.completion = completion;
        this.systemPromptTemplate = systemPromptTemplate;
        this.userPromptTemplate = userPromptTemplate;
    }
    
    public String compress(...) {
        String systemPrompt = systemPromptTemplate.render(PromptRenderContext.empty());
        String userPrompt = userPromptTemplate.render(context);
        
        CompletionRequest request = CompletionRequest.builder()
                .messages(List.of(
                        SystemCompletionMessage.of(systemPrompt),
                        UserCompletionMessage.of(userPrompt)
                ))
                .build();
    }
}

// 修改后：系统 Prompt 由外部传入（无预设），用户模板内置且不可替换
public class LLMMemoryCompressionStrategy implements MemoryCompressionStrategy {
    public static final String DEFAULT_USER_TEMPLATE =
            "{{memoryItems}}{{messages}}";
    
    private final LLMCompletion completion;
    private final PromptTemplate systemPromptTemplate;
    private final PromptTemplate userPromptTemplate;
    
    public LLMMemoryCompressionStrategy(LLMCompletion completion, PromptTemplate systemPromptTemplate) {
        this.completion = completion;
        this.systemPromptTemplate = systemPromptTemplate;
        this.userPromptTemplate = new DefaultPromptTemplate(DEFAULT_USER_TEMPLATE);
    }
    
    public String compress(...) {
        String systemPrompt = systemPromptTemplate.render(PromptRenderContext.empty());
        String userPrompt = userPromptTemplate.render(context);
        
        CompletionRequest request = CompletionRequest.builder()
                .messages(List.of(
                        SystemCompletionMessage.of(systemPrompt),
                        UserCompletionMessage.of(userPrompt)
                ))
                .build();
    }
}
```

### 变更 3（2026-04-19）：压缩指令移至系统 Prompt，用户模板仅保留占位符

**变更原因：**
评审提问确认：压缩指令应放在系统 Prompt 中，用户模板仅保留 `{{memoryItems}}`、`{{messages}}` 动态内容占位符。

**文件变更：**

| 文件路径 | 变更类型 | 说明 |
|----------|----------|------|
| `.agents/plan/011-Agent模块-MultipleSQLLiteMemory设计方案.md` | 修改 | `DEFAULT_USER_TEMPLATE` 移除压缩指令文本，仅保留 `{{memoryItems}}{{messages}}`；压缩指令由外部 `systemPromptTemplate` 负责；更新类注释、Prompt 说明章节、测试断言 |

**关键代码变更：**

```java
// 修改前：用户模板内置完整压缩指令
public static final String DEFAULT_USER_TEMPLATE =
        "你是一个专业的对话记忆压缩助手...\n\n" +
        "压缩要求：...\n\n" +
        "{{memoryItems}}" +
        "{{messages}}";

// 修改后：用户模板仅保留动态内容占位符，压缩指令由外部 systemPromptTemplate 提供
public static final String DEFAULT_USER_TEMPLATE =
        "{{memoryItems}}" +
        "{{messages}}";
```

## 6. 评审记录

| 评审人 | 时间 | 结论 | 备注 |
|--------|------|------|------|
| [待填写] | [待填写] | ❌ 需修改 | Prompt 必须使用 Prompt 模块 |
| [待填写] | [待填写] | ❌ 需修改 | 1. SYSTEM_PROMPT 无需预设 2. USER_TEMPLATE 无需从外部指定，使用预设模板即可 |
| Owner | 2026-04-19 | ✅ 通过 | |
