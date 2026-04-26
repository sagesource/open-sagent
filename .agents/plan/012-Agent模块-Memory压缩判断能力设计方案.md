# 方案：012-Agent模块-Memory压缩判断能力设计方案

## 1. 背景与目的

### 1.1 背景

当前 Memory 模块的 `compress()` 方法内部包含了压缩条件判断逻辑（滑动窗口阈值检查），调用方无法在执行压缩前独立获知"当前是否需要进行压缩"。这导致：

1. **调用方无法提前决策**：Agent 或外部调度器在调用 `compress()` 前，无法判断本次调用是否会实际执行压缩（还是返回 skipped）。
2. **判断逻辑与压缩行为耦合**：`SimpleMemory` 和 `MultipleSQLLiteMemory` 的 `compress()` 方法中，均内嵌了相同的滑动窗口阈值判断逻辑，代码重复且分散在各实现类中。
3. **不利于扩展更复杂的判断策略**：未来可能需要基于 Token 数量、消息内容特征、时间间隔等更复杂的条件来判断是否需要压缩，当前架构难以平滑扩展。

### 1.2 目的

1. 在 `Memory` 核心接口中定义独立的 `shouldCompress()` 方法，使调用方可以在压缩前独立判断是否需要执行压缩。
2. 将各实现类中内嵌的判断逻辑抽取到 `shouldCompress()` 方法中，消除代码重复，实现判断逻辑与压缩执行逻辑的解耦。
3. 保持现有 `compress()` 方法的行为不变（向后兼容），`compress()` 内部复用 `shouldCompress()` 进行前置判断。
4. 为后续扩展更复杂的压缩条件判断策略预留接口能力。

## 2. 修改方案

### 2.1 模块职责边界

```
open-sagent-core (抽象定义层)
    ├── Memory                      Memory抽象接口（新增 shouldCompress 方法）

open-sagent-infrastructure (具体实现层)
    ├── SimpleMemory                实现 shouldCompress（基于滑动窗口阈值）
    └── MultipleSQLLiteMemory       实现 shouldCompress（基于滑动窗口阈值）
```

### 2.2 文件变更列表

#### open-sagent-core

| 文件路径 | 变更类型 | 说明 |
|----------|----------|------|
| `open-sagent-core/src/main/java/ai/sagesource/opensagent/core/agent/memory/Memory.java` | 修改 | Memory 接口新增 `shouldCompress()` 方法 |

#### open-sagent-infrastructure

| 文件路径 | 变更类型 | 说明 |
|----------|----------|------|
| `open-sagent-infrastructure/src/main/java/ai/sagesource/opensagent/infrastructure/agent/memory/SimpleMemory.java` | 修改 | 新增 `shouldCompress()` 实现，复用到 `compress()` 中 |
| `open-sagent-infrastructure/src/main/java/ai/sagesource/opensagent/infrastructure/agent/memory/MultipleSQLLiteMemory.java` | 修改 | 新增 `shouldCompress()` 实现，复用到 `compress()` 中 |
| `open-sagent-infrastructure/src/test/java/ai/sagesource/opensagent/infrastructure/agent/memory/SimpleMemoryTest.java` | 修改 | 补充 `shouldCompress()` 相关单元测试 |
| `open-sagent-infrastructure/src/test/java/ai/sagesource/opensagent/infrastructure/agent/memory/MultipleSQLLiteMemoryTest.java` | 修改 | 补充 `shouldCompress()` 相关单元测试 |

### 2.3 详细变更内容

#### 文件 1: `open-sagent-core/src/main/java/ai/sagesource/opensagent/core/agent/memory/Memory.java`

在 `Memory` 接口中新增 `shouldCompress()` 方法：

```java
package ai.sagesource.opensagent.core.agent.memory;

import ai.sagesource.opensagent.core.llm.message.CompletionMessage;

import java.util.List;

/**
 * Memory抽象接口
 * <p>
 * 定义Agent记忆管理的基本行为，包括对话历史的添加、查询和记忆压缩
 *
 * @author: sage.xue
 * @time: 2026/4/14
 */
public interface Memory {

    /**
     * 添加对话历史
     *
     * @param message 对话消息
     */
    void addMessage(CompletionMessage message);

    /**
     * 批量添加对话历史
     *
     * @param messages 对话消息列表
     */
    void addMessages(List<CompletionMessage> messages);

    /**
     * 获取所有对话历史（完整的历史对话信息列表）
     *
     * @return 对话消息列表
     */
    List<CompletionMessage> getMessages();

    /**
     * 获取未压缩的对话历史
     *
     * @return 未压缩的对话消息列表
     */
    List<CompletionMessage> getUncompressedMessages();

    /**
     * 获取所有压缩后的记忆历史
     *
     * @return 记忆项列表
     */
    List<MemoryItem> getMemoryItems();

    /**
     * 判断当前是否需要进行记忆压缩
     * <p>
     * 调用方可在执行 {@link #compress()} 之前调用此方法，
     * 独立判断当前记忆状态是否满足压缩条件。
     *
     * @return true 表示需要进行压缩，false 表示暂无需压缩
     */
    boolean shouldCompress();

    /**
     * 执行记忆压缩并保存
     * <p>
     * 使用最新的记忆历史 + 未压缩对话历史 进行记忆压缩，压缩完成后清空未压缩对话历史
     *
     * @return 压缩结果
     */
    CompressionResult compress();

    /**
     * 清空所有对话历史和记忆
     */
    void clear();

    /**
     * 获取最后一条对话消息的ID
     *
     * @return 消息ID，可能为null
     */
    String getLastMessageId();

    /**
     * 获取最后一条记忆项的ID
     *
     * @return 记忆项ID，可能为null
     */
    String getLastMemoryItemId();
}
```

#### 文件 2: `open-sagent-infrastructure/src/main/java/ai/sagesource/opensagent/infrastructure/agent/memory/SimpleMemory.java`

新增 `shouldCompress()` 实现，并将 `compress()` 中的前置判断逻辑复用该方法：

```java
package ai.sagesource.opensagent.infrastructure.agent.memory;

import ai.sagesource.opensagent.core.agent.memory.*;
import ai.sagesource.opensagent.core.llm.message.CompletionMessage;
import ai.sagesource.opensagent.core.llm.message.MessageRole;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 基于内存的简单Memory实现
 * <p>
 * 将某一个对话窗的对话历史保存在内存中；
 * 使用滑动时间窗模式进行记忆压缩，压缩时保留最近窗口大小的会话信息；
 * 压缩时剔除SYSTEM消息，多个TOOL消息只保留最新一次
 *
 * @author: sage.xue
 * @time: 2026/4/14
 */
@Slf4j
public class SimpleMemory implements Memory {

    /**
     * 默认滑动窗口大小
     */
    private static final int DEFAULT_WINDOW_SIZE = 50;

    /**
     * 滑动窗口大小（每次压缩时保留的最近未压缩消息数量）
     */
    private final int windowSize;

    /**
     * 完整对话历史
     */
    private final List<CompletionMessage> messages = new ArrayList<>();

    /**
     * 未压缩对话历史
     */
    private final List<CompletionMessage> uncompressedMessages = new ArrayList<>();

    /**
     * 记忆历史
     */
    private final List<MemoryItem> memoryItems = new ArrayList<>();

    public SimpleMemory() {
        this(DEFAULT_WINDOW_SIZE);
    }

    public SimpleMemory(int windowSize) {
        this.windowSize = windowSize > 0 ? windowSize : DEFAULT_WINDOW_SIZE;
    }

    @Override
    public void addMessage(CompletionMessage message) {
        if (message == null) {
            return;
        }
        synchronized (this) {
            messages.add(message);
            uncompressedMessages.add(message);
        }
        log.info("> Memory | 添加消息成功, role: {} <", message.getRole());
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
        synchronized (this) {
            return new ArrayList<>(messages);
        }
    }

    @Override
    public List<CompletionMessage> getUncompressedMessages() {
        synchronized (this) {
            return new ArrayList<>(uncompressedMessages);
        }
    }

    @Override
    public List<MemoryItem> getMemoryItems() {
        synchronized (this) {
            return new ArrayList<>(memoryItems);
        }
    }

    @Override
    public boolean shouldCompress() {
        synchronized (this) {
            if (uncompressedMessages.isEmpty()) {
                return false;
            }
            int compressCount = Math.max(0, uncompressedMessages.size() - windowSize);
            return compressCount > 0;
        }
    }

    @Override
    public CompressionResult compress() {
        synchronized (this) {
            if (uncompressedMessages.isEmpty()) {
                log.warn("> Memory | 无可压缩的对话历史 <");
                return CompressionResult.skipped("无可压缩的对话历史");
            }

            if (!shouldCompress()) {
                log.warn("> Memory | 未压缩消息数量未超过滑动窗口阈值 {}，无需压缩 <", windowSize);
                return CompressionResult.skipped("未压缩消息数量未超过滑动窗口阈值，无需压缩");
            }

            int compressCount = Math.max(0, uncompressedMessages.size() - windowSize);

            List<CompletionMessage> toCompress = new ArrayList<>(uncompressedMessages.subList(0, compressCount));

            // 找出最后一个 TOOL 消息的索引
            int lastToolIndex = -1;
            for (int i = 0; i < toCompress.size(); i++) {
                if (toCompress.get(i).getRole() == MessageRole.TOOL) {
                    lastToolIndex = i;
                }
            }

            StringBuilder sb = new StringBuilder();
            String lastMsgId = null;
            for (int i = 0; i < toCompress.size(); i++) {
                CompletionMessage msg = toCompress.get(i);
                if (msg.getRole() == MessageRole.SYSTEM) {
                    continue;
                }
                if (msg.getRole() == MessageRole.TOOL && i != lastToolIndex) {
                    continue;
                }
                sb.append("[").append(msg.getRole()).append("]: ").append(msg.getTextContent()).append("\n");
                if (msg.getMessageId() != null) {
                    lastMsgId = msg.getMessageId();
                }
            }

            // 清除已压缩部分
            for (int i = 0; i < compressCount; i++) {
                uncompressedMessages.remove(0);
            }

            // 如果过滤后没有有效内容，不生成 MemoryItem
            if (sb.length() == 0) {
                log.info("> Memory | 过滤后无可压缩的有效对话历史 <");
                return CompressionResult.skipped("过滤后无可压缩的有效对话历史");
            }

            String lastMemoryItemId = memoryItems.isEmpty() ? null : memoryItems.get(memoryItems.size() - 1).getMemoryItemId();

            MemoryItem item = MemoryItem.builder()
                    .memoryItemId(UUID.randomUUID().toString())
                    .content(sb.toString().trim())
                    .lastMessageId(lastMsgId)
                    .lastMemoryItemId(lastMemoryItemId)
                    .timestamp(System.currentTimeMillis())
                    .build();

            memoryItems.add(item);

            log.info("> Memory | 简单压缩完成, memoryItemId: {}, 压缩消息数: {}, 保留消息数: {} <",
                    item.getMemoryItemId(), compressCount, uncompressedMessages.size());
            return CompressionResult.success(item);
        }
    }

    @Override
    public void clear() {
        synchronized (this) {
            messages.clear();
            uncompressedMessages.clear();
            memoryItems.clear();
        }
        log.info("> Memory | 清空所有记忆 <");
    }

    @Override
    public String getLastMessageId() {
        synchronized (this) {
            if (messages.isEmpty()) {
                return null;
            }
            CompletionMessage last = messages.get(messages.size() - 1);
            return last != null ? last.getMessageId() : null;
        }
    }

    @Override
    public String getLastMemoryItemId() {
        synchronized (this) {
            if (memoryItems.isEmpty()) {
                return null;
            }
            return memoryItems.get(memoryItems.size() - 1).getMemoryItemId();
        }
    }
}
```

#### 文件 3: `open-sagent-infrastructure/src/main/java/ai/sagesource/opensagent/infrastructure/agent/memory/MultipleSQLLiteMemory.java`

新增 `shouldCompress()` 实现，并将 `compress()` 中的前置判断逻辑复用该方法：

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
    public boolean shouldCompress() {
        List<CompletionMessage> uncompressed = getUncompressedMessages();
        if (uncompressed.isEmpty()) {
            return false;
        }
        int compressCount = Math.max(0, uncompressed.size() - windowSize);
        return compressCount > 0;
    }

    @Override
    public CompressionResult compress() {
        if (!shouldCompress()) {
            log.warn("> Memory | session: {} 未压缩消息数量未超过滑动窗口阈值 {}，无需压缩 <", sessionId, windowSize);
            return CompressionResult.skipped("未压缩消息数量未超过滑动窗口阈值，无需压缩");
        }

        List<CompletionMessage> uncompressed = getUncompressedMessages();

        int compressCount = Math.max(0, uncompressed.size() - windowSize);

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

#### 文件 4: `open-sagent-infrastructure/src/test/java/ai/sagesource/opensagent/infrastructure/agent/memory/SimpleMemoryTest.java`

在 `SimpleMemoryTest` 中补充 `shouldCompress()` 相关测试用例：

```java
package ai.sagesource.opensagent.infrastructure.agent.memory;

import ai.sagesource.opensagent.core.agent.memory.CompressionResult;
import ai.sagesource.opensagent.core.agent.memory.MemoryItem;
import ai.sagesource.opensagent.core.llm.message.AssistantCompletionMessage;
import ai.sagesource.opensagent.core.llm.message.CompletionMessage;
import ai.sagesource.opensagent.core.llm.message.SystemCompletionMessage;
import ai.sagesource.opensagent.core.llm.message.ToolCompletionMessage;
import ai.sagesource.opensagent.core.llm.message.UserCompletionMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SimpleMemory单元测试
 *
 * @author: sage.xue
 * @time: 2026/4/14
 */
class SimpleMemoryTest {

    @Test
    @DisplayName("添加单条消息 - 成功")
    void testAddMessage() {
        SimpleMemory memory = new SimpleMemory();
        CompletionMessage message = UserCompletionMessage.of("你好");

        memory.addMessage(message);

        assertEquals(1, memory.getMessages().size());
        assertEquals(1, memory.getUncompressedMessages().size());
    }

    @Test
    @DisplayName("批量添加消息 - 成功")
    void testAddMessages() {
        SimpleMemory memory = new SimpleMemory();
        List<CompletionMessage> messages = Arrays.asList(
                UserCompletionMessage.of("你好"),
                AssistantCompletionMessage.of("你好，有什么可以帮你的？")
        );

        memory.addMessages(messages);

        assertEquals(2, memory.getMessages().size());
        assertEquals(2, memory.getUncompressedMessages().size());
    }

    @Test
    @DisplayName("压缩消息 - 成功")
    void testCompress() {
        SimpleMemory memory = new SimpleMemory();
        memory.addMessage(UserCompletionMessage.of("今天天气怎么样？"));
        memory.addMessage(AssistantCompletionMessage.of("今天是晴天。"));

        CompressionResult result = memory.compress();

        assertTrue(result.isSuccess());
        assertNotNull(result.getMemoryItem());
        assertEquals(0, memory.getUncompressedMessages().size());
        assertEquals(1, memory.getMemoryItems().size());
    }

    @Test
    @DisplayName("无消息时压缩 - 返回跳过")
    void testCompressWhenEmpty() {
        SimpleMemory memory = new SimpleMemory();

        CompressionResult result = memory.compress();

        assertFalse(result.isSuccess());
        assertEquals("无可压缩的对话历史", result.getMessage());
    }

    @Test
    @DisplayName("清空记忆 - 成功")
    void testClear() {
        SimpleMemory memory = new SimpleMemory();
        memory.addMessage(UserCompletionMessage.of("你好"));
        memory.compress();

        memory.clear();

        assertTrue(memory.getMessages().isEmpty());
        assertTrue(memory.getUncompressedMessages().isEmpty());
        assertTrue(memory.getMemoryItems().isEmpty());
    }

    @Test
    @DisplayName("获取最后消息ID - 成功")
    void testGetLastMessageId() {
        SimpleMemory memory = new SimpleMemory();
        UserCompletionMessage message = UserCompletionMessage.builder()
                .messageId("msg-001")
                .contents(new java.util.ArrayList<>(List.of(
                        ai.sagesource.opensagent.core.llm.message.TextContent.builder().text("你好").build())))
                .build();

        memory.addMessage(message);

        assertEquals("msg-001", memory.getLastMessageId());
    }

    @Test
    @DisplayName("获取最后记忆项ID - 成功")
    void testGetLastMemoryItemId() {
        SimpleMemory memory = new SimpleMemory();
        memory.addMessage(UserCompletionMessage.of("你好"));
        CompressionResult result = memory.compress();

        assertNotNull(memory.getLastMemoryItemId());
        assertEquals(result.getMemoryItem().getMemoryItemId(), memory.getLastMemoryItemId());
    }

    @Test
    @DisplayName("压缩结果包含关联ID - 成功")
    void testCompressionResultContainsRelationIds() {
        SimpleMemory memory = new SimpleMemory();
        UserCompletionMessage msg1 = UserCompletionMessage.builder()
                .messageId("msg-001")
                .contents(new java.util.ArrayList<>(List.of(
                        ai.sagesource.opensagent.core.llm.message.TextContent.builder().text("你好").build())))
                .build();
        memory.addMessage(msg1);

        CompressionResult first = memory.compress();
        assertNotNull(first.getMemoryItem());
        assertEquals("msg-001", first.getMemoryItem().getLastMessageId());
        assertNull(first.getMemoryItem().getLastMemoryItemId());

        UserCompletionMessage msg2 = UserCompletionMessage.builder()
                .messageId("msg-002")
                .contents(new java.util.ArrayList<>(List.of(
                        ai.sagesource.opensagent.core.llm.message.TextContent.builder().text("再见").build())))
                .build();
        memory.addMessage(msg2);

        CompressionResult second = memory.compress();
        assertNotNull(second.getMemoryItem());
        assertEquals("msg-002", second.getMemoryItem().getLastMessageId());
        assertEquals(first.getMemoryItem().getMemoryItemId(), second.getMemoryItem().getLastMemoryItemId());
    }

    @Test
    @DisplayName("滑动时间窗压缩 - 保留窗口内消息")
    void testCompressWithSlidingWindow() {
        SimpleMemory memory = new SimpleMemory(3);
        memory.addMessage(UserCompletionMessage.of("msg1"));
        memory.addMessage(UserCompletionMessage.of("msg2"));
        memory.addMessage(UserCompletionMessage.of("msg3"));
        memory.addMessage(UserCompletionMessage.of("msg4"));
        memory.addMessage(UserCompletionMessage.of("msg5"));

        CompressionResult result = memory.compress();

        assertTrue(result.isSuccess());
        assertEquals(3, memory.getUncompressedMessages().size());
        assertEquals(5, memory.getMessages().size());
    }

    @Test
    @DisplayName("压缩时未超过窗口阈值 - 返回跳过")
    void testCompressWhenBelowWindowThreshold() {
        SimpleMemory memory = new SimpleMemory(5);
        memory.addMessage(UserCompletionMessage.of("msg1"));
        memory.addMessage(UserCompletionMessage.of("msg2"));

        CompressionResult result = memory.compress();

        assertFalse(result.isSuccess());
        assertEquals("未压缩消息数量未超过滑动窗口阈值，无需压缩", result.getMessage());
    }

    @Test
    @DisplayName("压缩时剔除SYSTEM消息 - 成功")
    void testCompressFiltersSystemMessages() {
        SimpleMemory memory = new SimpleMemory(1);
        memory.addMessage(SystemCompletionMessage.of("系统提示"));
        memory.addMessage(UserCompletionMessage.of("你好"));

        CompressionResult result = memory.compress();

        assertTrue(result.isSuccess());
        assertNotNull(result.getMemoryItem());
        assertFalse(result.getMemoryItem().getContent().contains("SYSTEM"));
        assertTrue(result.getMemoryItem().getContent().contains("USER"));
    }

    @Test
    @DisplayName("压缩时TOOL消息只保留最新一次 - 成功")
    void testCompressKeepsLatestToolMessageOnly() {
        SimpleMemory memory = new SimpleMemory(1);
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
    @DisplayName("过滤后无有效消息 - 返回跳过并清除已处理部分")
    void testCompressAllFiltered() {
        SimpleMemory memory = new SimpleMemory(1);
        memory.addMessage(SystemCompletionMessage.of("系统提示1"));
        memory.addMessage(SystemCompletionMessage.of("系统提示2"));
        memory.addMessage(UserCompletionMessage.of("你好"));

        CompressionResult result = memory.compress();

        assertFalse(result.isSuccess());
        assertEquals("过滤后无可压缩的有效对话历史", result.getMessage());
        assertEquals(1, memory.getUncompressedMessages().size());
    }

    // ===== shouldCompress 相关测试 =====

    @Test
    @DisplayName("shouldCompress-空记忆返回false")
    void testShouldCompressWhenEmpty() {
        SimpleMemory memory = new SimpleMemory();
        assertFalse(memory.shouldCompress());
    }

    @Test
    @DisplayName("shouldCompress-未超过窗口阈值返回false")
    void testShouldCompressWhenBelowThreshold() {
        SimpleMemory memory = new SimpleMemory(5);
        memory.addMessage(UserCompletionMessage.of("msg1"));
        memory.addMessage(UserCompletionMessage.of("msg2"));

        assertFalse(memory.shouldCompress());
    }

    @Test
    @DisplayName("shouldCompress-超过窗口阈值返回true")
    void testShouldCompressWhenAboveThreshold() {
        SimpleMemory memory = new SimpleMemory(3);
        memory.addMessage(UserCompletionMessage.of("msg1"));
        memory.addMessage(UserCompletionMessage.of("msg2"));
        memory.addMessage(UserCompletionMessage.of("msg3"));
        memory.addMessage(UserCompletionMessage.of("msg4"));
        memory.addMessage(UserCompletionMessage.of("msg5"));

        assertTrue(memory.shouldCompress());
    }

    @Test
    @DisplayName("shouldCompress-压缩后再次判断返回false")
    void testShouldCompressAfterCompress() {
        SimpleMemory memory = new SimpleMemory(3);
        memory.addMessage(UserCompletionMessage.of("msg1"));
        memory.addMessage(UserCompletionMessage.of("msg2"));
        memory.addMessage(UserCompletionMessage.of("msg3"));
        memory.addMessage(UserCompletionMessage.of("msg4"));
        memory.addMessage(UserCompletionMessage.of("msg5"));

        assertTrue(memory.shouldCompress());
        memory.compress();
        assertFalse(memory.shouldCompress());
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

#### 文件 5: `open-sagent-infrastructure/src/test/java/ai/sagesource/opensagent/infrastructure/agent/memory/MultipleSQLLiteMemoryTest.java`

在 `MultipleSQLLiteMemoryTest` 中补充 `shouldCompress()` 相关测试用例（仅展示新增部分，完整文件与方案 011 一致）：

```java
    @Test
    @DisplayName("shouldCompress-空记忆返回false")
    void testShouldCompressWhenEmpty() {
        MultipleSQLLiteMemory memory = createMemory();
        assertFalse(memory.shouldCompress());
    }

    @Test
    @DisplayName("shouldCompress-未超过窗口阈值返回false")
    void testShouldCompressWhenBelowThreshold() {
        MultipleSQLLiteMemory memory = new MultipleSQLLiteMemory(
                UUID.randomUUID().toString(), TEST_DB, 10, null, null);
        memory.addMessage(UserCompletionMessage.of("msg1"));
        memory.addMessage(UserCompletionMessage.of("msg2"));

        assertFalse(memory.shouldCompress());
    }

    @Test
    @DisplayName("shouldCompress-超过窗口阈值返回true")
    void testShouldCompressWhenAboveThreshold() {
        MultipleSQLLiteMemory memory = createMemory();
        memory.addMessage(UserCompletionMessage.of("msg1"));
        memory.addMessage(UserCompletionMessage.of("msg2"));
        memory.addMessage(UserCompletionMessage.of("msg3"));
        memory.addMessage(UserCompletionMessage.of("msg4"));
        memory.addMessage(UserCompletionMessage.of("msg5"));

        assertTrue(memory.shouldCompress());
    }

    @Test
    @DisplayName("shouldCompress-压缩后再次判断返回false")
    void testShouldCompressAfterCompress() {
        MultipleSQLLiteMemory memory = createMemory();
        memory.addMessage(UserCompletionMessage.of("msg1"));
        memory.addMessage(UserCompletionMessage.of("msg2"));
        memory.addMessage(UserCompletionMessage.of("msg3"));
        memory.addMessage(UserCompletionMessage.of("msg4"));
        memory.addMessage(UserCompletionMessage.of("msg5"));

        assertTrue(memory.shouldCompress());
        memory.compress();
        assertFalse(memory.shouldCompress());
    }
```

## 3. 影响范围分析

### 3.1 模块依赖关系

```
open-sagent-core (抽象定义层)
    ├── Memory                      新增 shouldCompress() 方法

open-sagent-infrastructure (具体实现层)
    ├── SimpleMemory                新增 shouldCompress() 实现，compress() 复用
    └── MultipleSQLLiteMemory       新增 shouldCompress() 实现，compress() 复用
```

### 3.2 影响范围

| 模块 | 影响说明 |
|------|----------|
| open-sagent-core | `Memory` 接口新增 `shouldCompress()` 方法，为增量扩展，不破坏现有实现 |
| open-sagent-infrastructure | `SimpleMemory` 和 `MultipleSQLLiteMemory` 新增 `shouldCompress()` 实现，并复用到 `compress()` 中，无破坏性变更 |
| open-sagent-tools | 无直接影响 |
| open-sagent-web | 无直接影响 |
| open-sagent-cli | 无直接影响 |

### 3.3 向后兼容性说明

1. **接口新增方法**：`Memory` 接口新增 `shouldCompress()` 方法，现有自定义实现类需要补充实现，属于 Java 接口新增方法的常规兼容策略。
2. **`compress()` 行为不变**：`compress()` 的返回值、日志输出、压缩逻辑均保持不变，仅将内部前置判断提取为 `shouldCompress()` 调用。
3. **滑动窗口阈值判断逻辑不变**：判断条件仍为 `uncompressedMessages.size() > windowSize`，各实现类的行为与变更前完全一致。

## 4. 测试计划

### 4.1 单元测试

| 测试类 | 模块 | 测试内容 |
|--------|------|----------|
| `SimpleMemoryTest` | infrastructure | 补充 `shouldCompress()` 空记忆、未超阈值、超过阈值、压缩后再判断等场景 |
| `MultipleSQLLiteMemoryTest` | infrastructure | 补充 `shouldCompress()` 空记忆、未超阈值、超过阈值、压缩后再判断等场景 |

### 4.2 编译验证

```bash
mvn clean compile test-compile -pl open-sagent-core,open-sagent-infrastructure -am
```

## 5. 评审记录

| 评审人 | 时间 | 结论 | 备注 |
|--------|------|------|------|
| sage | 2026/04/26 | 通过 | |
