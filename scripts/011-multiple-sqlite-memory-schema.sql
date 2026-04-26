-- ============================================================
-- 方案 011: MultipleSQLLiteMemory 数据库表结构
-- 说明: 基于 SQLite 的持久化 Memory 实现，支持多会话隔离
-- ============================================================

-- sessions 表：会话元数据
CREATE TABLE IF NOT EXISTS sessions (
    session_id    TEXT PRIMARY KEY,
    window_size   INTEGER DEFAULT 50,
    created_at    BIGINT,
    updated_at    BIGINT
);

-- messages 表：消息存储
CREATE TABLE IF NOT EXISTS messages (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    session_id      TEXT NOT NULL,
    message_id      TEXT,
    role            TEXT NOT NULL,
    content_json    TEXT NOT NULL,
    sequence        INTEGER NOT NULL,
    is_uncompressed INTEGER DEFAULT 1,
    created_at      BIGINT
);

-- memory_items 表：压缩后的记忆项
CREATE TABLE IF NOT EXISTS memory_items (
    memory_item_id      TEXT PRIMARY KEY,
    session_id          TEXT NOT NULL,
    content             TEXT NOT NULL,
    last_message_id     TEXT,
    last_memory_item_id TEXT,
    timestamp           BIGINT
);

-- ============================================================
-- 索引：优化查询性能
-- ============================================================

-- 按会话+顺序查询消息（getMessages / getUncompressedMessages / getLastMessageId）
CREATE INDEX IF NOT EXISTS idx_messages_session_sequence
    ON messages (session_id, sequence);

-- 按会话+未压缩状态查询（markCompressed / getUncompressedMessages）
CREATE INDEX IF NOT EXISTS idx_messages_session_uncompressed_sequence
    ON messages (session_id, is_uncompressed, sequence);

-- 按会话+时间戳查询记忆项（getMemoryItems / getLastMemoryItemId）
CREATE INDEX IF NOT EXISTS idx_memory_items_session_timestamp
    ON memory_items (session_id, timestamp);
