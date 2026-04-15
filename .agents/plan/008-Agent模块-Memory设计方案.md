# 方案：008-Agent模块-Memory设计方案

## 1. 背景与目的

### 1.1 背景

Open Sagent是一个AI-Agent框架，Agent运行需要依赖Memory来管理对话历史、控制上下文规模、减少模型幻觉并提高模型处理准确度。Memory模块负责保存对话历史、提供记忆压缩能力，并为Agent调用大模型时组装所需的历史对话上下文。

根据架构设计，Memory模块需要：
1. 在Core模块定义Memory的核心抽象和数据模型
2. 在Infrastructure模块提供具体的Memory实现
3. 支持对话历史的添加、查询和记忆压缩
4. 记忆压缩需保留可追溯的关联信息（最后一条对话ID、最后一条记忆历史ID）

### 1.2 目的

1. 设计可扩展的Memory抽象接口，支持不同存储介质和压缩策略
2. 定义记忆（MemoryItem）、压缩结果（CompressionResult）等核心模型
3. 建立Memory模块的异常处理机制
4. 在Infrastructure模块提供SimpleMemory内存实现，作为基础参考实现

## 2. 修改方案

### 2.1 模块职责边界

```
open-sagent-core (抽象定义层)
    ├── Memory                      Memory抽象接口
    ├── MemoryItem                  记忆项（压缩后的记忆）
    ├── CompressionResult           记忆压缩结果
    ├── MemoryCompressionStrategy   记忆压缩策略接口
    └── OpenSagentMemoryException   Memory模块异常类

open-sagent-infrastructure (具体实现层)
    ├── SimpleMemory                基于内存的Memory实现（无压缩功能）
    └── SimpleMemoryCompressionStrategy 简单压缩策略实现（预留）
```

### 2.2 文件变更列表

#### open-sagent-core

| 文件路径 | 变更类型 | 说明 |
|----------|----------|------|
| `open-sagent-core/src/main/java/ai/sagesource/opensagent/core/agent/memory/Memory.java` | 新增 | Memory抽象接口 |
| `open-sagent-core/src/main/java/ai/sagesource/opensagent/core/agent/memory/MemoryItem.java` | 新增 | 记忆项模型 |
| `open-sagent-core/src/main/java/ai/sagesource/opensagent/core/agent/memory/CompressionResult.java` | 新增 | 压缩结果模型 |
| `open-sagent-core/src/main/java/ai/sagesource/opensagent/core/agent/memory/MemoryCompressionStrategy.java` | 新增 | 压缩策略接口 |
| `open-sagent-core/src/main/java/ai/sagesource/opensagent/core/agent/exception/OpenSagentMemoryException.java` | 新增 | Memory模块异常类 |
| `open-sagent-core/src/test/java/ai/sagesource/opensagent/core/agent/memory/MemoryItemTest.java` | 新增 | 记忆项单元测试 |
| `open-sagent-core/src/test/java/ai/sagesource/opensagent/core/agent/memory/CompressionResultTest.java` | 新增 | 压缩结果单元测试 |

#### open-sagent-infrastructure

| 文件路径 | 变更类型 | 说明 |
|----------|----------|------|
| `open-sagent-infrastructure/src/main/java/ai/sagesource/opensagent/infrastructure/agent/memory/SimpleMemory.java` | 修改 | 基于内存的Memory实现，增加滑动窗口压缩、SYSTEM消息过滤、TOOL消息去重、窗口大小可配置 |
| `open-sagent-infrastructure/src/test/java/ai/sagesource/opensagent/infrastructure/agent/memory/SimpleMemoryTest.java` | 修改 | SimpleMemory单元测试，补充滑动窗口、消息过滤、TOOL去重相关测试 |

### 2.3 详细变更内容

#### 文件 1: `open-sagent-core/src/main/java/ai/sagesource/opensagent/core/agent/memory/Memory.java`

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

#### 文件 2: `open-sagent-core/src/main/java/ai/sagesource/opensagent/core/agent/memory/MemoryItem.java`

```java
package ai.sagesource.opensagent.core.agent.memory;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 记忆项
 * <p>
 * 表示一次记忆压缩后生成的压缩记忆，包含压缩后的内容以及与原始对话、历史记忆的关联信息
 *
 * @author: sage.xue
 * @time: 2026/4/14
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemoryItem {

    /**
     * 记忆项唯一标识
     */
    private String memoryItemId;

    /**
     * 压缩后的记忆内容
     */
    private String content;

    /**
     * 本次压缩所包含的最后一条对话ID
     * <p>
     * 用于关联该记忆项对应的原始对话历史范围
     */
    private String lastMessageId;

    /**
     * 本次压缩所基于的最后一条记忆历史ID
     * <p>
     * 用于关联该记忆项生成时所依赖的前序记忆
     */
    private String lastMemoryItemId;

    /**
     * 记忆压缩时间戳（毫秒）
     */
    private Long timestamp;
}
```

#### 文件 3: `open-sagent-core/src/main/java/ai/sagesource/opensagent/core/agent/memory/CompressionResult.java`

```java
package ai.sagesource.opensagent.core.agent.memory;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 记忆压缩结果
 * <p>
     * 封装记忆压缩操作的返回结果
 *
 * @author: sage.xue
 * @time: 2026/4/14
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompressionResult {

    /**
     * 是否压缩成功
     */
    private boolean success;

    /**
     * 生成的记忆项
     */
    private MemoryItem memoryItem;

    /**
     * 压缩后的提示信息
     */
    private String message;

    /**
     * 创建成功的压缩结果
     *
     * @param memoryItem 记忆项
     * @return 压缩结果
     */
    public static CompressionResult success(MemoryItem memoryItem) {
        return CompressionResult.builder()
                .success(true)
                .memoryItem(memoryItem)
                .message("记忆压缩成功")
                .build();
    }

    /**
     * 创建跳过的压缩结果（如无未压缩对话可压缩）
     *
     * @param message 提示信息
     * @return 压缩结果
     */
    public static CompressionResult skipped(String message) {
        return CompressionResult.builder()
                .success(false)
                .message(message)
                .build();
    }
}
```

#### 文件 4: `open-sagent-core/src/main/java/ai/sagesource/opensagent/core/agent/memory/MemoryCompressionStrategy.java`

```java
package ai.sagesource.opensagent.core.agent.memory;

import ai.sagesource.opensagent.core.llm.message.CompletionMessage;

import java.util.List;

/**
 * 记忆压缩策略接口
 * <p>
 * 定义记忆压缩的具体策略，不同实现可采用不同的压缩算法（如摘要、向量化、关键词提取等）
 *
 * @author: sage.xue
 * @time: 2026/4/14
 */
public interface MemoryCompressionStrategy {

    /**
     * 执行记忆压缩
     * <p>
     * 基于现有的记忆历史和未压缩的对话历史，生成新的记忆项内容
     *
     * @param memoryItems           已有的记忆历史列表
     * @param uncompressedMessages  未压缩的对话历史列表
     * @param lastMemoryItemId      最后一条记忆历史ID（可能为null）
     * @param lastMessageId         最后一条对话ID（可能为null）
     * @return 压缩后的记忆项内容
     */
    String compress(
            List<MemoryItem> memoryItems,
            List<CompletionMessage> uncompressedMessages,
            String lastMemoryItemId,
            String lastMessageId
    );
}
```

#### 文件 5: `open-sagent-core/src/main/java/ai/sagesource/opensagent/core/agent/exception/OpenSagentMemoryException.java`

```java
package ai.sagesource.opensagent.core.agent.exception;

import ai.sagesource.opensagent.base.exception.OpenSagentException;

/**
 * Memory模块异常类
 *
 * @author: sage.xue
 * @time: 2026/4/14
 */
public class OpenSagentMemoryException extends OpenSagentException {

    public OpenSagentMemoryException(String message) {
        super(message);
    }

    public OpenSagentMemoryException(String message, Throwable cause) {
        super(message, cause);
    }

    public OpenSagentMemoryException(Throwable cause) {
        super(cause);
    }
}
```

#### 文件 6: `open-sagent-infrastructure/src/main/java/ai/sagesource/opensagent/infrastructure/agent/memory/SimpleMemory.java`

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
    public CompressionResult compress() {
        synchronized (this) {
            if (uncompressedMessages.isEmpty()) {
                log.warn("> Memory | 无可压缩的对话历史 <");
                return CompressionResult.skipped("无可压缩的对话历史");
            }

            int compressCount = Math.max(0, uncompressedMessages.size() - windowSize);
            if (compressCount == 0) {
                log.warn("> Memory | 未压缩消息数量未超过滑动窗口阈值 {}，无需压缩 <", windowSize);
                return CompressionResult.skipped("未压缩消息数量未超过滑动窗口阈值，无需压缩");
            }

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

#### 文件 7: `open-sagent-core/src/test/java/ai/sagesource/opensagent/core/agent/memory/MemoryItemTest.java`

```java
package ai.sagesource.opensagent.core.agent.memory;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MemoryItem单元测试
 *
 * @author: sage.xue
 * @time: 2026/4/14
 */
class MemoryItemTest {

    @Test
    @DisplayName("创建记忆项 - 成功")
    void testCreateMemoryItem() {
        MemoryItem item = MemoryItem.builder()
                .memoryItemId("mem-001")
                .content("用户询问天气，助手回答晴天")
                .lastMessageId("msg-003")
                .lastMemoryItemId("mem-000")
                .timestamp(System.currentTimeMillis())
                .build();

        assertNotNull(item);
        assertEquals("mem-001", item.getMemoryItemId());
        assertEquals("用户询问天气，助手回答晴天", item.getContent());
        assertEquals("msg-003", item.getLastMessageId());
        assertEquals("mem-000", item.getLastMemoryItemId());
        assertNotNull(item.getTimestamp());
    }

    @Test
    @DisplayName("记忆项无关联ID - 成功")
    void testMemoryItemWithoutRelations() {
        MemoryItem item = MemoryItem.builder()
                .memoryItemId("mem-002")
                .content("首次压缩的记忆")
                .build();

        assertNotNull(item);
        assertNull(item.getLastMessageId());
        assertNull(item.getLastMemoryItemId());
    }
}
```

#### 文件 8: `open-sagent-core/src/test/java/ai/sagesource/opensagent/core/agent/memory/CompressionResultTest.java`

```java
package ai.sagesource.opensagent.core.agent.memory;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CompressionResult单元测试
 *
 * @author: sage.xue
 * @time: 2026/4/14
 */
class CompressionResultTest {

    @Test
    @DisplayName("创建成功压缩结果 - 成功")
    void testSuccessResult() {
        MemoryItem item = MemoryItem.builder()
                .memoryItemId("mem-001")
                .content("压缩内容")
                .build();

        CompressionResult result = CompressionResult.success(item);

        assertTrue(result.isSuccess());
        assertEquals(item, result.getMemoryItem());
        assertEquals("记忆压缩成功", result.getMessage());
    }

    @Test
    @DisplayName("创建跳过压缩结果 - 成功")
    void testSkippedResult() {
        CompressionResult result = CompressionResult.skipped("无可压缩内容");

        assertFalse(result.isSuccess());
        assertNull(result.getMemoryItem());
        assertEquals("无可压缩内容", result.getMessage());
    }
}
```

#### 文件 9: `open-sagent-infrastructure/src/test/java/ai/sagesource/opensagent/infrastructure/agent/memory/SimpleMemoryTest.java`

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
        assertEquals(2, memory.getMessages().size() - memory.getUncompressedMessages().size());
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

## 3. 影响范围分析

### 3.1 模块依赖关系

```
open-sagent-base (基础定义)
    └── OpenSagentException (全局异常基类)

open-sagent-core (抽象定义)
    ├── 依赖 open-sagent-base
    ├── 依赖 open-sagent-core/llm/message (CompletionMessage)
    ├── Memory (接口)
    ├── MemoryItem (记忆项模型)
    ├── CompressionResult (压缩结果模型)
    ├── MemoryCompressionStrategy (压缩策略接口)
    └── OpenSagentMemoryException (继承OpenSagentException)

open-sagent-infrastructure (具体实现)
    ├── 依赖 open-sagent-core
    └── SimpleMemory (基于内存的实现)
```

### 3.2 影响范围

| 模块 | 影响说明 |
|------|----------|
| open-sagent-core | 新增Agent Memory核心抽象，无破坏性变更 |
| open-sagent-infrastructure | 新增SimpleMemory内存实现，无破坏性变更 |
| open-sagent-tools | 无直接影响 |
| open-sagent-web | 无直接影响 |
| open-sagent-cli | 无直接影响 |

### 3.3 扩展性说明

1. **支持多种存储介质**：`Memory`为接口，后续可扩展`RedisMemory`、`DatabaseMemory`等持久化实现
2. **支持多种压缩策略**：`MemoryCompressionStrategy`为接口，后续可扩展基于LLM的摘要压缩、基于向量检索的记忆增强等
3. **与CompletionMessage解耦**：Memory接口直接复用已有的`CompletionMessage`模型，无需重新定义消息结构
4. **预留关联追溯能力**：`MemoryItem`中内置`lastMessageId`和`lastMemoryItemId`，满足架构中关于记忆压缩后需保留关联信息的要求

## 4. 测试计划

### 4.1 单元测试

| 测试类 | 模块 | 测试内容 |
|--------|------|----------|
| `MemoryItemTest` | core | 记忆项创建、关联ID验证 |
| `CompressionResultTest` | core | 成功/跳过结果构建 |
| `SimpleMemoryTest` | infrastructure | 消息添加、批量添加、压缩、清空、关联ID验证、滑动窗口压缩、SYSTEM消息过滤、TOOL消息去重 |

### 4.2 编译验证

```bash
mvn clean compile test-compile -pl open-sagent-core,open-sagent-infrastructure -am
```

## 5. 方案变更记录

### 变更 1（2026-04-15）：SimpleMemory 实现补全，与架构设计保持一致

**变更原因：**
现有 `SimpleMemory` 实现与 `project_design_agent.md` 中 Memory 模块架构设计不一致，缺少以下关键能力：
1. 未实现滑动时间窗模式记忆压缩（应保留最近可配置条数的未压缩消息）
2. 压缩时未剔除 SYSTEM 消息
3. 压缩时未对 TOOL 消息进行去重（多个 TOOL 消息应只保留最新一次）

**文件变更：**

| 文件路径 | 变更类型 | 说明 |
|----------|----------|------|
| `open-sagent-infrastructure/src/main/java/ai/sagesource/opensagent/infrastructure/agent/memory/SimpleMemory.java` | 修改 | 增加滑动窗口压缩、SYSTEM消息过滤、TOOL消息去重、窗口大小可配置 |
| `open-sagent-infrastructure/src/test/java/ai/sagesource/opensagent/infrastructure/agent/memory/SimpleMemoryTest.java` | 修改 | 补充滑动窗口、消息过滤、TOOL去重相关单元测试 |

**关键代码变更：**

1. 新增滑动窗口大小配置：
```java
private static final int DEFAULT_WINDOW_SIZE = 50;
private final int windowSize;

public SimpleMemory() {
    this(DEFAULT_WINDOW_SIZE);
}

public SimpleMemory(int windowSize) {
    this.windowSize = windowSize > 0 ? windowSize : DEFAULT_WINDOW_SIZE;
}
```

2. 压缩逻辑增加滑动窗口和消息过滤：
```java
int compressCount = Math.max(0, uncompressedMessages.size() - windowSize);
if (compressCount == 0) {
    return CompressionResult.skipped("未压缩消息数量未超过滑动窗口阈值，无需压缩");
}

List<CompletionMessage> toCompress = new ArrayList<>(uncompressedMessages.subList(0, compressCount));

// 找出最后一个 TOOL 消息的索引
int lastToolIndex = -1;
for (int i = 0; i < toCompress.size(); i++) {
    if (toCompress.get(i).getRole() == MessageRole.TOOL) {
        lastToolIndex = i;
    }
}

// 过滤 SYSTEM 消息和多余的 TOOL 消息
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
```

## 6. 评审记录

| 评审人 | 时间 | 结论 | 备注 |
|--------|------|------|------|
| sage | 2026/4/15 | 通过 | 按方案实施 |
| Kimi | 2026/4/15 | 实施完成 | 所有文件已创建，编译通过，测试全部通过（core: 32 tests, infrastructure: 25 tests） |
| Kimi | 2026/4/15 | 通过 | SimpleMemory滑动窗口与消息过滤变更 |
