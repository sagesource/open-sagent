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
        if (completion != null && fallbackStrategy != null) {
            compressedContent = fallbackStrategy.compress(existingItems, filtered, lastMemoryItemId, lastMsgId);
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
