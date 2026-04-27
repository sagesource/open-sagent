# 方案 014: 数据库初始化 SQL 与 SQLite 数据库创建 SKILL 设计方案

## 评审记录

| 轮次 | 评审人 | 评审结果 | 评审意见 |
|------|--------|----------|----------|
| 1    | sage   | 不通过   | 新增功能-将代码中的建表逻辑移除，工程运行前会预先创建好所有的表 |
| 2    |        |          |          |

---

## 1. 需求背景

当前工程存在两个数据存储体系：
- **Web 模块**：使用 JPA/Hibernate 管理关系型数据库，包含 `sys_user`、`conversation`、`chat_message` 3张表
- **Memory 模块**：使用原生 JDBC 操作 SQLite 文件数据库，包含 `sessions`、`messages`、`memory_items` 3张表

**核心变更点**：MultipleSQLLiteMemory 中通过 `initDatabase()` 在运行时自动创建 SQLite 表，现需将该逻辑移除。所有数据库表改为**工程运行前预先创建**，代码中不再包含任何 `CREATE TABLE` 逻辑。

## 2. 设计目标

1. **移除代码中的自动建表逻辑**：MultipleSQLLiteMemory 不再自动创建表和插入 session 记录
2. **生成完整的 `scripts/init-schema.sql`**：包含 Memory + Web 全部6张表的建表语句和索引，作为预初始化脚本
3. **修改 MultipleSQLLiteMemory 构造函数**：移除 `initDatabase()` 和 `ensureSession()` 调用，改为要求外部预先初始化
4. **生成引用类 SKILL `sqlite-database-init.md`**：说明工程运行前如何预先创建 SQLite 数据库文件并执行建表语句
5. **更新 `SKILLS.md`** 列表

## 3. 数据库表结构设计

### 3.1 Web 模块表（JPA 实体映射）

#### sys_user 表

| 字段名 | 类型 | 约束 | 说明 |
|--------|------|------|------|
| id | BIGINT | PRIMARY KEY, AUTO_INCREMENT | 主键 |
| email | VARCHAR(128) | UNIQUE, NOT NULL | 邮箱（登录账号） |
| password_hash | VARCHAR(256) | NOT NULL | 加密后的密码 |
| nickname | VARCHAR(64) | | 对话昵称 |
| created_at | DATETIME | NOT NULL | 创建时间 |
| updated_at | DATETIME | NOT NULL | 更新时间 |

#### conversation 表

| 字段名 | 类型 | 约束 | 说明 |
|--------|------|------|------|
| id | BIGINT | PRIMARY KEY, AUTO_INCREMENT | 主键 |
| user_id | BIGINT | NOT NULL | 所属用户ID |
| session_id | VARCHAR(64) | UNIQUE, NOT NULL | 会话唯一标识（与 MultipleSQLLiteMemory 关联） |
| title | VARCHAR(256) | | 对话标题 |
| agent_version | VARCHAR(16) | NOT NULL | Agent版本：simple / smart |
| created_at | DATETIME | NOT NULL | 创建时间 |
| updated_at | DATETIME | NOT NULL | 更新时间 |

#### chat_message 表

| 字段名 | 类型 | 约束 | 说明 |
|--------|------|------|------|
| id | BIGINT | PRIMARY KEY, AUTO_INCREMENT | 主键 |
| conversation_id | BIGINT | NOT NULL | 所属对话ID |
| role | VARCHAR(16) | NOT NULL | 消息角色：user / assistant / tool |
| content | TEXT | NOT NULL | 消息内容 |
| created_at | DATETIME | NOT NULL | 创建时间 |

### 3.2 Memory 模块表（SQLite）

#### sessions 表

| 字段名 | 类型 | 约束 | 说明 |
|--------|------|------|------|
| session_id | TEXT | PRIMARY KEY | 会话唯一标识 |
| window_size | INTEGER | DEFAULT 50 | 滑动窗口大小 |
| created_at | BIGINT | | 创建时间戳 |
| updated_at | BIGINT | | 更新时间戳 |

#### messages 表

| 字段名 | 类型 | 约束 | 说明 |
|--------|------|------|------|
| id | INTEGER | PRIMARY KEY AUTOINCREMENT | 主键 |
| session_id | TEXT | NOT NULL | 所属会话 |
| message_id | TEXT | | 消息唯一标识 |
| role | TEXT | NOT NULL | 消息角色 |
| content_json | TEXT | NOT NULL | 消息 JSON 序列化 |
| sequence | INTEGER | NOT NULL | 消息顺序 |
| is_uncompressed | INTEGER | DEFAULT 1 | 是否未压缩 |
| created_at | BIGINT | | 创建时间戳 |

#### memory_items 表

| 字段名 | 类型 | 约束 | 说明 |
|--------|------|------|------|
| memory_item_id | TEXT | PRIMARY KEY | 记忆项唯一标识 |
| session_id | TEXT | NOT NULL | 所属会话 |
| content | TEXT | NOT NULL | 压缩后的记忆内容 |
| last_message_id | TEXT | | 关联的最后消息ID |
| last_memory_item_id | TEXT | | 关联的上一个记忆项ID |
| timestamp | BIGINT | | 时间戳 |

## 4. SQL 文件设计

### 文件路径

`scripts/init-schema.sql`

### 文件内容结构

```sql
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

-- sys_user 表
CREATE TABLE IF NOT EXISTS sys_user (
    id            BIGINT PRIMARY KEY AUTO_INCREMENT,
    email         VARCHAR(128) UNIQUE NOT NULL,
    password_hash VARCHAR(256) NOT NULL,
    nickname      VARCHAR(64),
    created_at    DATETIME NOT NULL,
    updated_at    DATETIME NOT NULL
);

-- conversation 表
CREATE TABLE IF NOT EXISTS conversation (
    id            BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id       BIGINT NOT NULL,
    session_id    VARCHAR(64) UNIQUE NOT NULL,
    title         VARCHAR(256),
    agent_version VARCHAR(16) NOT NULL,
    created_at    DATETIME NOT NULL,
    updated_at    DATETIME NOT NULL
);

-- chat_message 表
CREATE TABLE IF NOT EXISTS chat_message (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    conversation_id BIGINT NOT NULL,
    role            VARCHAR(16) NOT NULL,
    content         TEXT NOT NULL,
    created_at      DATETIME NOT NULL
);

-- Web 模块索引
CREATE INDEX IF NOT EXISTS idx_conversation_user_id ON conversation (user_id);
CREATE INDEX IF NOT EXISTS idx_chat_message_conversation_id ON chat_message (conversation_id);

-- --------------------------------------------------------
-- 第二部分: Memory 模块表 (SQLite 专用)
-- --------------------------------------------------------

-- sessions 表
CREATE TABLE IF NOT EXISTS sessions (
    session_id    TEXT PRIMARY KEY,
    window_size   INTEGER DEFAULT 50,
    created_at    BIGINT,
    updated_at    BIGINT
);

-- messages 表
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

-- memory_items 表
CREATE TABLE IF NOT EXISTS memory_items (
    memory_item_id      TEXT PRIMARY KEY,
    session_id          TEXT NOT NULL,
    content             TEXT NOT NULL,
    last_message_id     TEXT,
    last_memory_item_id TEXT,
    timestamp           BIGINT
);

-- Memory 模块索引
CREATE INDEX IF NOT EXISTS idx_messages_session_sequence ON messages (session_id, sequence);
CREATE INDEX IF NOT EXISTS idx_messages_session_uncompressed_sequence ON messages (session_id, is_uncompressed, sequence);
CREATE INDEX IF NOT EXISTS idx_memory_items_session_timestamp ON memory_items (session_id, timestamp);
```

## 5. 代码变更设计

### MultipleSQLLiteMemory.java 变更

**移除以下方法调用和内容：**

1. **构造函数**中移除 `initDatabase()` 和 `ensureSession()` 调用
2. **删除 `initDatabase()` 方法**：不再自动创建 sessions、messages、memory_items 表
3. **删除 `ensureSession()` 方法**：不再自动插入 session 记录

**变更后的构造函数逻辑：**
```java
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
    // 不再调用 initDatabase() 和 ensureSession()
    // 表结构和 session 记录由外部预先初始化
}
```

**保留的方法：**
- `getConnection()` — 获取数据库连接
- `addMessage()` / `addMessages()` — 添加消息
- `getMessages()` / `getUncompressedMessages()` — 查询消息
- `getMemoryItems()` — 查询记忆项
- `shouldCompress()` / `compress()` — 压缩逻辑
- `markCompressed()` — 标记已压缩
- `saveMemoryItem()` — 保存记忆项
- `updateSessionTime()` — 更新会话时间
- `clear()` — 清空记忆
- `getLastMessageId()` / `getLastMemoryItemId()` — 获取最后ID
- `getNextSequence()` — 获取下一条序列号

## 6. 引用类 SKILL 设计

### 文件路径

`.agents/skills/references/sqlite-database-init.md`

### SKILL 内容要点

1. **核心理念**：SQLite 数据库文件和表结构在工程运行前预先创建，代码中只负责数据操作
2. **预先创建方式**：
   - 方式一：命令行执行 `sqlite3 memory.db < scripts/init-schema.sql`
   - 方式二：Maven/Gradle 构建阶段插件执行 SQL
   - 方式三：Docker 启动时挂载已初始化的数据库文件
3. **JDBC 连接**：仅需 `jdbc:sqlite:memory.db`，数据库文件会自动创建（但表需预先初始化）
4. **Maven 依赖**：
   ```xml
   <dependency>
       <groupId>org.xerial</groupId>
       <artifactId>sqlite-jdbc</artifactId>
       <version>3.45.0.0</version>
   </dependency>
   ```
5. **代码示例**：展示如何在不包含建表逻辑的情况下使用 SQLite

## 7. 变更范围

### 新增文件

| 文件路径 | 说明 |
|----------|------|
| `scripts/init-schema.sql` | 统一的数据库预初始化脚本（6张表 + 索引） |
| `.agents/skills/references/sqlite-database-init.md` | SQLite 数据库预创建与初始化 SKILL |

### 修改文件

| 文件路径 | 说明 |
|----------|------|
| `open-sagent-infrastructure/.../MultipleSQLLiteMemory.java` | 移除 `initDatabase()` 和 `ensureSession()` 方法及构造函数调用 |
| `.agents/skills/SKILLS.md` | 新增引用类 SKILL 条目 |

---

## 8. 备注

- 现有 `scripts/011-multiple-sqlite-memory-schema.sql` 保留，作为方案 011 的历史产物
- 新的 `scripts/init-schema.sql` 为全量统一预初始化脚本
- Web 模块表由 JPA 管理，在实际部署中 JPA 的 `ddl-auto` 配置决定是否需要手动执行 Web 模块的建表语句；此处 SQL 文件作为文档和备用初始化手段
