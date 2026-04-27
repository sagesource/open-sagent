-- ============================================================
-- Open Sagent 数据库初始化脚本
-- 说明: 工程运行前预先执行此脚本创建所有表结构
--       代码中不再包含任何 CREATE TABLE 逻辑
-- 包含: Web 模块表 (sys_user, conversation, chat_message)
--       Memory 模块表 (sessions, messages, memory_items)
-- ============================================================

-- --------------------------------------------------------
-- 第一部分: Web 模块表 (标准 SQL，适配 MySQL/PostgreSQL/SQLite)
-- --------------------------------------------------------

-- sys_user 表: 用户实体，邮箱作为登录账号
CREATE TABLE IF NOT EXISTS sys_user (
    id            BIGINT PRIMARY KEY AUTOINCREMENT,
    email         VARCHAR(128) UNIQUE NOT NULL,
    password_hash VARCHAR(256) NOT NULL,
    nickname      VARCHAR(64),
    created_at    DATETIME NOT NULL,
    updated_at    DATETIME NOT NULL
);

-- conversation 表: 对话实体，session_id 关联 MultipleSQLLiteMemory
CREATE TABLE IF NOT EXISTS conversation (
    id            BIGINT PRIMARY KEY AUTOINCREMENT,
    user_id       BIGINT NOT NULL,
    session_id    VARCHAR(64) UNIQUE NOT NULL,
    title         VARCHAR(256),
    agent_version VARCHAR(16) NOT NULL,
    created_at    DATETIME NOT NULL,
    updated_at    DATETIME NOT NULL
);

-- chat_message 表: 聊天消息实体，用于前端快速加载消息列表
CREATE TABLE IF NOT EXISTS chat_message (
    id              BIGINT PRIMARY KEY AUTOINCREMENT,
    conversation_id BIGINT NOT NULL,
    role            VARCHAR(16) NOT NULL,
    content         TEXT NOT NULL,
    created_at      DATETIME NOT NULL
);

-- Web 模块索引
CREATE INDEX IF NOT EXISTS idx_conversation_user_id
    ON conversation (user_id);

CREATE INDEX IF NOT EXISTS idx_chat_message_conversation_id
    ON chat_message (conversation_id);

-- --------------------------------------------------------
-- 第二部分: Memory 模块表 (SQLite 专用)
-- --------------------------------------------------------

-- sessions 表: 会话元数据，记录滑动窗口大小和时间戳
CREATE TABLE IF NOT EXISTS sessions (
    session_id    TEXT PRIMARY KEY,
    window_size   INTEGER DEFAULT 50,
    created_at    BIGINT,
    updated_at    BIGINT
);

-- messages 表: 消息存储，content_json 保存完整消息序列化
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

-- memory_items 表: 压缩后的记忆项存储
CREATE TABLE IF NOT EXISTS memory_items (
    memory_item_id      TEXT PRIMARY KEY,
    session_id          TEXT NOT NULL,
    content             TEXT NOT NULL,
    last_message_id     TEXT,
    last_memory_item_id TEXT,
    timestamp           BIGINT
);

-- Memory 模块索引
-- 按会话+顺序查询消息（getMessages / getUncompressedMessages / getLastMessageId）
CREATE INDEX IF NOT EXISTS idx_messages_session_sequence
    ON messages (session_id, sequence);

-- 按会话+未压缩状态查询（markCompressed / getUncompressedMessages）
CREATE INDEX IF NOT EXISTS idx_messages_session_uncompressed_sequence
    ON messages (session_id, is_uncompressed, sequence);

-- 按会话+时间戳查询记忆项（getMemoryItems / getLastMemoryItemId）
CREATE INDEX IF NOT EXISTS idx_memory_items_session_timestamp
    ON memory_items (session_id, timestamp);
