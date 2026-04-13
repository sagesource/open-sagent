# 方案：002-LLM模块-Message设计方案

## 1. 背景与目的

### 1.1 背景

Open Sagent框架需要与各大厂商的LLM进行对话交互。为了屏蔽不同厂商的API消息格式差异，需要在Core模块中定义统一的消息抽象模型。Message模块是Completion模块的基础，定义了对话中的各类消息角色和内容格式。

### 1.2 目的

1. 设计统一的消息模型体系，支持System、User、Assistant、Developer、Tool等多种角色
2. 支持纯文本、多模态内容（图片、文件等）
3. 文件内容支持BASE64内嵌或URI引用两种方式
4. 为Completion模块提供标准化的消息输入/输出格式

## 2. 修改方案

### 2.1 文件变更列表

| 文件路径 | 变更类型 | 说明 |
|----------|----------|------|
| `open-sagent-core/src/main/java/ai/sagesource/opensagent/core/llm/message/CompletionMessage.java` | 新增 | 消息基类抽象接口 |
| `open-sagent-core/src/main/java/ai/sagesource/opensagent/core/llm/message/MessageRole.java` | 新增 | 消息角色枚举 |
| `open-sagent-core/src/main/java/ai/sagesource/opensagent/core/llm/message/MessageContent.java` | 新增 | 消息内容抽象接口 |
| `open-sagent-core/src/main/java/ai/sagesource/opensagent/core/llm/message/TextContent.java` | 新增 | 文本内容实现 |
| `open-sagent-core/src/main/java/ai/sagesource/opensagent/core/llm/message/ImageContent.java` | 新增 | 图片内容实现 |
| `open-sagent-core/src/main/java/ai/sagesource/opensagent/core/llm/message/FileContent.java` | 新增 | 文件内容实现 |
| `open-sagent-core/src/main/java/ai/sagesource/opensagent/core/llm/message/ContentType.java` | 新增 | 内容类型枚举 |
| `open-sagent-core/src/main/java/ai/sagesource/opensagent/core/llm/message/UserMessage.java` | 新增 | 用户消息实现 |
| `open-sagent-core/src/main/java/ai/sagesource/opensagent/core/llm/message/SystemMessage.java` | 新增 | 系统消息实现 |
| `open-sagent-core/src/main/java/ai/sagesource/opensagent/core/llm/message/AssistantMessage.java` | 新增 | 助手消息实现 |
| `open-sagent-core/src/main/java/ai/sagesource/opensagent/core/llm/message/DeveloperMessage.java` | 新增 | 开发者消息实现 |
| `open-sagent-core/src/main/java/ai/sagesource/opensagent/core/llm/message/ToolMessage.java` | 新增 | 工具消息实现 |
| `open-sagent-core/src/main/java/ai/sagesource/opensagent/core/llm/message/MessageUtils.java` | 新增 | 消息工具类 |
| `open-sagent-core/src/test/java/ai/sagesource/opensagent/core/llm/message/CompletionMessageTest.java` | 新增 | 消息模型单元测试 |

### 2.2 详细变更内容

#### 文件 1: `open-sagent-core/src/main/java/ai/sagesource/opensagent/core/llm/message/MessageRole.java`

```java
package ai.sagesource.opensagent.core.llm.message;

/**
 * 消息角色枚举
 * <p>
 * 定义LLM对话中参与者的角色类型
 *
 * @author: sage.xue
 * @time: 2026/4/13
 */
public enum MessageRole {

    /**
     * 系统角色：定义AI助手的行为和上下文
     */
    SYSTEM,

    /**
     * 用户角色：发送请求的人
     */
    USER,

    /**
     * 助手角色：AI模型的回复
     */
    ASSISTANT,

    /**
     * 开发者角色：用于向模型提供指令（OpenAI o1系列模型支持）
     */
    DEVELOPER,

    /**
     * 工具角色：工具调用的结果
     */
    TOOL
}
```

#### 文件 2: `open-sagent-core/src/main/java/ai/sagesource/opensagent/core/llm/message/ContentType.java`

```java
package ai.sagesource.opensagent.core.llm.message;

/**
 * 消息内容类型枚举
 * <p>
 * 定义消息内容的媒体类型
 *
 * @author: sage.xue
 * @time: 2026/4/13
 */
public enum ContentType {

    /**
     * 纯文本内容
     */
    TEXT,

    /**
     * 图片内容
     */
    IMAGE,

    /**
     * 文件内容（文档、PDF等）
     */
    FILE
}
```

#### 文件 3: `open-sagent-core/src/main/java/ai/sagesource/opensagent/core/llm/message/MessageContent.java`

```java
package ai.sagesource.opensagent.core.llm.message;

/**
 * 消息内容抽象接口
 * <p>
 * 定义消息内容的统一接口，支持文本、图片、文件等多种类型
 *
 * @author: sage.xue
 * @time: 2026/4/13
 */
public interface MessageContent {

    /**
     * 获取内容类型
     *
     * @return 内容类型枚举
     */
    ContentType getType();

    /**
     * 获取纯文本表示
     * <p>
     * 对于非文本内容，返回描述性文本
     *
     * @return 文本内容
     */
    String getText();
}
```

#### 文件 4: `open-sagent-core/src/main/java/ai/sagesource/opensagent/core/llm/message/TextContent.java`

```java
package ai.sagesource.opensagent.core.llm.message;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文本内容实现
 * <p>
 * 纯文本消息内容
 *
 * @author: sage.xue
 * @time: 2026/4/13
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TextContent implements MessageContent {

    /**
     * 文本内容
     */
    private String text;

    @Override
    public ContentType getType() {
        return ContentType.TEXT;
    }

    @Override
    public String getText() {
        return text;
    }
}
```

#### 文件 5: `open-sagent-core/src/main/java/ai/sagesource/opensagent/core/llm/message/ImageContent.java`

```java
package ai.sagesource.opensagent.core.llm.message;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 图片内容实现
 * <p>
 * 支持BASE64编码或URL引用的图片内容
 *
 * @author: sage.xue
 * @time: 2026/4/13
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImageContent implements MessageContent {

    /**
     * 图片URL（可以是HTTP URL或data URL）
     */
    private String url;

    /**
     * BASE64编码的图片数据
     * <p>
     * 与url字段二选一，优先使用url
     */
    private String base64Data;

    /**
     * 图片MIME类型（如image/png, image/jpeg）
     */
    @Builder.Default
    private String mimeType = "image/png";

    /**
     * 图片详细描述（可选）
     */
    private String detail;

    @Override
    public ContentType getType() {
        return ContentType.IMAGE;
    }

    @Override
    public String getText() {
        if (url != null && !url.isEmpty()) {
            return "[图片: " + url + "]";
        }
        return "[图片数据]";
    }

    /**
     * 获取图片数据源
     * <p>
     * 优先返回URL，如果没有URL则返回BASE64格式的data URL
     *
     * @return 可用的图片数据源
     */
    public String getImageSource() {
        if (url != null && !url.isEmpty()) {
            return url;
        }
        if (base64Data != null && !base64Data.isEmpty()) {
            return "data:" + mimeType + ";base64," + base64Data;
        }
        return null;
    }
}
```

#### 文件 6: `open-sagent-core/src/main/java/ai/sagesource/opensagent/core/llm/message/FileContent.java`

```java
package ai.sagesource.opensagent.core.llm.message;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文件内容实现
 * <p>
 * 支持BASE64编码或URL引用的文件内容
 *
 * @author: sage.xue
 * @time: 2026/4/13
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileContent implements MessageContent {

    /**
     * 文件URL
     */
    private String url;

    /**
     * BASE64编码的文件数据
     * <p>
     * 与url字段二选一，优先使用url
     */
    private String base64Data;

    /**
     * 文件MIME类型
     */
    private String mimeType;

    /**
     * 文件名
     */
    private String fileName;

    @Override
    public ContentType getType() {
        return ContentType.FILE;
    }

    @Override
    public String getText() {
        String name = fileName != null ? fileName : "文件";
        if (url != null && !url.isEmpty()) {
            return "[" + name + ": " + url + "]";
        }
        return "[" + name + "数据]";
    }

    /**
     * 获取文件数据源
     * <p>
     * 优先返回URL，如果没有URL则返回BASE64格式的data URL
     *
     * @return 可用的文件数据源
     */
    public String getFileSource() {
        if (url != null && !url.isEmpty()) {
            return url;
        }
        if (base64Data != null && !base64Data.isEmpty() && mimeType != null) {
            return "data:" + mimeType + ";base64," + base64Data;
        }
        return null;
    }
}
```

#### 文件 7: `open-sagent-core/src/main/java/ai/sagesource/opensagent/core/llm/message/CompletionMessage.java`

```java
package ai.sagesource.opensagent.core.llm.message;

import java.util.List;

/**
 * 消息基类抽象接口
 * <p>
 * 定义LLM对话消息的通用接口，所有具体消息类型都实现此接口
 *
 * @author: sage.xue
 * @time: 2026/4/13
 */
public interface CompletionMessage {

    /**
     * 获取消息角色
     *
     * @return 消息角色枚举
     */
    MessageRole getRole();

    /**
     * 获取消息内容列表
     * <p>
     * 支持多模态内容，列表中的每个元素代表一种内容（文本、图片、文件等）
     *
     * @return 内容列表
     */
    List<MessageContent> getContents();

    /**
     * 获取消息ID（可选）
     *
     * @return 消息唯一标识，可能为null
     */
    String getMessageId();

    /**
     * 获取纯文本内容
     * <p>
     * 将所有内容拼接成纯文本表示
     *
     * @return 纯文本内容
     */
    default String getTextContent() {
        if (getContents() == null || getContents().isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (MessageContent content : getContents()) {
            sb.append(content.getText());
        }
        return sb.toString();
    }

    /**
     * 添加内容
     *
     * @param content 要添加的内容
     */
    void addContent(MessageContent content);

    /**
     * 添加文本内容（便捷方法）
     *
     * @param text 文本内容
     */
    default void addText(String text) {
        addContent(TextContent.builder().text(text).build());
    }
}
```

#### 文件 8: `open-sagent-core/src/main/java/ai/sagesource/opensagent/core/llm/message/UserMessage.java`

```java
package ai.sagesource.opensagent.core.llm.message;

import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 用户消息实现
 * <p>
 * 表示用户发送的消息，支持文本、图片、文件等多种内容
 *
 * @author: sage.xue
 * @time: 2026/4/13
 */
@Data
@Builder
public class UserMessage implements CompletionMessage {

    /**
     * 消息ID
     */
    private String messageId;

    /**
     * 消息内容列表
     */
    @Builder.Default
    private List<MessageContent> contents = new ArrayList<>();

    @Override
    public MessageRole getRole() {
        return MessageRole.USER;
    }

    @Override
    public List<MessageContent> getContents() {
        return contents;
    }

    @Override
    public String getMessageId() {
        return messageId;
    }

    @Override
    public void addContent(MessageContent content) {
        if (this.contents == null) {
            this.contents = new ArrayList<>();
        }
        this.contents.add(content);
    }

    /**
     * 创建纯文本用户消息（便捷方法）
     *
     * @param text 文本内容
     * @return UserMessage实例
     */
    public static UserMessage of(String text) {
        return UserMessage.builder()
                .contents(new ArrayList<>(List.of(TextContent.builder().text(text).build())))
                .build();
    }
}
```

#### 文件 9: `open-sagent-core/src/main/java/ai/sagesource/opensagent/core/llm/message/SystemMessage.java`

```java
package ai.sagesource.opensagent.core.llm.message;

import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 系统消息实现
 * <p>
 * 用于设置AI助手的行为、角色和上下文
 *
 * @author: sage.xue
 * @time: 2026/4/13
 */
@Data
@Builder
public class SystemMessage implements CompletionMessage {

    /**
     * 消息ID
     */
    private String messageId;

    /**
     * 消息内容列表
     */
    @Builder.Default
    private List<MessageContent> contents = new ArrayList<>();

    @Override
    public MessageRole getRole() {
        return MessageRole.SYSTEM;
    }

    @Override
    public List<MessageContent> getContents() {
        return contents;
    }

    @Override
    public String getMessageId() {
        return messageId;
    }

    @Override
    public void addContent(MessageContent content) {
        if (this.contents == null) {
            this.contents = new ArrayList<>();
        }
        this.contents.add(content);
    }

    /**
     * 创建纯文本系统消息（便捷方法）
     *
     * @param text 系统提示文本
     * @return SystemMessage实例
     */
    public static SystemMessage of(String text) {
        return SystemMessage.builder()
                .contents(new ArrayList<>(List.of(TextContent.builder().text(text).build())))
                .build();
    }
}
```

#### 文件 10: `open-sagent-core/src/main/java/ai/sagesource/opensagent/core/llm/message/AssistantMessage.java`

```java
package ai.sagesource.opensagent.core.llm.message;

import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 助手消息实现
 * <p>
 * 表示AI助手（大模型）的回复消息
 *
 * @author: sage.xue
 * @time: 2026/4/13
 */
@Data
@Builder
public class AssistantMessage implements CompletionMessage {

    /**
     * 消息ID
     */
    private String messageId;

    /**
     * 消息内容列表
     */
    @Builder.Default
    private List<MessageContent> contents = new ArrayList<>();

    /**
     * 是否完成思考（适用于有推理过程的模型）
     */
    @Builder.Default
    private boolean reasoningComplete = true;

    /**
     * 推理内容（可选）
     */
    private String reasoningContent;

    @Override
    public MessageRole getRole() {
        return MessageRole.ASSISTANT;
    }

    @Override
    public List<MessageContent> getContents() {
        return contents;
    }

    @Override
    public String getMessageId() {
        return messageId;
    }

    @Override
    public void addContent(MessageContent content) {
        if (this.contents == null) {
            this.contents = new ArrayList<>();
        }
        this.contents.add(content);
    }

    /**
     * 创建纯文本助手消息（便捷方法）
     *
     * @param text 回复文本
     * @return AssistantMessage实例
     */
    public static AssistantMessage of(String text) {
        return AssistantMessage.builder()
                .contents(new ArrayList<>(List.of(TextContent.builder().text(text).build())))
                .build();
    }
}
```

#### 文件 11: `open-sagent-core/src/main/java/ai/sagesource/opensagent/core/llm/message/DeveloperMessage.java`

```java
package ai.sagesource.opensagent.core.llm.message;

import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 开发者消息实现
 * <p>
 * 用于向模型提供指令（OpenAI o1系列模型等支持）
 *
 * @author: sage.xue
 * @time: 2026/4/13
 */
@Data
@Builder
public class DeveloperMessage implements CompletionMessage {

    /**
     * 消息ID
     */
    private String messageId;

    /**
     * 消息内容列表
     */
    @Builder.Default
    private List<MessageContent> contents = new ArrayList<>();

    @Override
    public MessageRole getRole() {
        return MessageRole.DEVELOPER;
    }

    @Override
    public List<MessageContent> getContents() {
        return contents;
    }

    @Override
    public String getMessageId() {
        return messageId;
    }

    @Override
    public void addContent(MessageContent content) {
        if (this.contents == null) {
            this.contents = new ArrayList<>();
        }
        this.contents.add(content);
    }

    /**
     * 创建纯文本开发者消息（便捷方法）
     *
     * @param text 开发者指令文本
     * @return DeveloperMessage实例
     */
    public static DeveloperMessage of(String text) {
        return DeveloperMessage.builder()
                .contents(new ArrayList<>(List.of(TextContent.builder().text(text).build())))
                .build();
    }
}
```

#### 文件 12: `open-sagent-core/src/main/java/ai/sagesource/opensagent/core/llm/message/ToolMessage.java`

```java
package ai.sagesource.opensagent.core.llm.message;

import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 工具消息实现
 * <p>
 * 表示工具调用的结果消息
 *
 * @author: sage.xue
 * @time: 2026/4/13
 */
@Data
@Builder
public class ToolMessage implements CompletionMessage {

    /**
     * 消息ID
     */
    private String messageId;

    /**
     * 工具调用ID
     */
    private String toolCallId;

    /**
     * 工具名称
     */
    private String toolName;

    /**
     * 消息内容列表
     */
    @Builder.Default
    private List<MessageContent> contents = new ArrayList<>();

    @Override
    public MessageRole getRole() {
        return MessageRole.TOOL;
    }

    @Override
    public List<MessageContent> getContents() {
        return contents;
    }

    @Override
    public String getMessageId() {
        return messageId;
    }

    @Override
    public void addContent(MessageContent content) {
        if (this.contents == null) {
            this.contents = new ArrayList<>();
        }
        this.contents.add(content);
    }

    /**
     * 创建工具结果消息（便捷方法）
     *
     * @param toolCallId 工具调用ID
     * @param toolName   工具名称
     * @param result     工具执行结果文本
     * @return ToolMessage实例
     */
    public static ToolMessage of(String toolCallId, String toolName, String result) {
        return ToolMessage.builder()
                .toolCallId(toolCallId)
                .toolName(toolName)
                .contents(new ArrayList<>(List.of(TextContent.builder().text(result).build())))
                .build();
    }
}
```

#### 文件 13: `open-sagent-core/src/main/java/ai/sagesource/opensagent/core/llm/message/MessageUtils.java`

```java
package ai.sagesource.opensagent.core.llm.message;

import java.util.ArrayList;
import java.util.List;

/**
 * 消息工具类
 * <p>
 * 提供消息创建、转换等便捷方法
 *
 * @author: sage.xue
 * @time: 2026/4/13
 */
public final class MessageUtils {

    private MessageUtils() {
        // 工具类禁止实例化
    }

    /**
     * 创建用户文本消息
     *
     * @param text 文本内容
     * @return UserMessage
     */
    public static CompletionMessage user(String text) {
        return UserMessage.of(text);
    }

    /**
     * 创建系统文本消息
     *
     * @param text 文本内容
     * @return SystemMessage
     */
    public static CompletionMessage system(String text) {
        return SystemMessage.of(text);
    }

    /**
     * 创建助手文本消息
     *
     * @param text 文本内容
     * @return AssistantMessage
     */
    public static CompletionMessage assistant(String text) {
        return AssistantMessage.of(text);
    }

    /**
     * 创建开发者文本消息
     *
     * @param text 文本内容
     * @return DeveloperMessage
     */
    public static CompletionMessage developer(String text) {
        return DeveloperMessage.of(text);
    }

    /**
     * 创建工具结果消息
     *
     * @param toolCallId 工具调用ID
     * @param toolName   工具名称
     * @param result     工具执行结果
     * @return ToolMessage
     */
    public static CompletionMessage tool(String toolCallId, String toolName, String result) {
        return ToolMessage.of(toolCallId, toolName, result);
    }

    /**
     * 创建包含图片的用户消息
     *
     * @param text     文本内容
     * @param imageUrl 图片URL
     * @return UserMessage
     */
    public static UserMessage userWithImage(String text, String imageUrl) {
        List<MessageContent> contents = new ArrayList<>();
        contents.add(TextContent.builder().text(text).build());
        contents.add(ImageContent.builder().url(imageUrl).build());
        return UserMessage.builder().contents(contents).build();
    }

    /**
     * 创建包含BASE64图片的用户消息
     *
     * @param text       文本内容
     * @param base64Data BASE64编码的图片数据
     * @param mimeType   图片MIME类型
     * @return UserMessage
     */
    public static UserMessage userWithImageBase64(String text, String base64Data, String mimeType) {
        List<MessageContent> contents = new ArrayList<>();
        contents.add(TextContent.builder().text(text).build());
        contents.add(ImageContent.builder()
                .base64Data(base64Data)
                .mimeType(mimeType)
                .build());
        return UserMessage.builder().contents(contents).build();
    }

    /**
     * 将消息列表转换为纯文本摘要
     *
     * @param messages 消息列表
     * @return 文本摘要
     */
    public static String toTextSummary(List<CompletionMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (CompletionMessage message : messages) {
            sb.append("[").append(message.getRole()).append("]: ");
            sb.append(message.getTextContent());
            sb.append("\n");
        }
        return sb.toString().trim();
    }
}
```

## 3. 影响范围分析

### 3.1 模块依赖关系

```
open-sagent-core (抽象定义)
    ├── CompletionMessage (消息基类接口)
    ├── MessageRole (角色枚举)
    ├── MessageContent (内容接口)
    ├── TextContent / ImageContent / FileContent (内容实现)
    └── UserMessage / SystemMessage / AssistantMessage / DeveloperMessage / ToolMessage (消息实现)

open-sagent-infrastructure (依赖)
    └── 将实现CompletionMessage到OpenAI SDK消息的转换
```

### 3.2 影响范围

| 模块 | 影响说明 |
|------|----------|
| open-sagent-core | 新增Message模块核心抽象，无破坏性变更 |
| open-sagent-infrastructure | 后续需实现消息转换器，将框架消息转为各厂商SDK消息格式 |
| open-sagent-tools | 无直接影响，可使用Message模型构建工具调用 |
| open-sagent-web | 无直接影响，可使用Message模型进行API交互 |
| open-sagent-cli | 无直接影响 |

### 3.3 扩展性说明

1. **新增内容类型**：通过实现`MessageContent`接口可支持新的媒体类型（如音频、视频）
2. **新增消息角色**：通过实现`CompletionMessage`接口可支持新的角色类型
3. **厂商适配**：Infrastructure层将实现`CompletionMessage`到各厂商SDK消息的转换器
4. **工具调用**：ToolMessage为未来Function Calling功能预留

## 4. 测试计划

### 4.1 单元测试

| 测试类 | 测试内容 |
|--------|----------|
| `CompletionMessageTest` | 消息创建、内容添加、角色验证 |
| `MessageContentTest` | 文本/图片/文件内容创建与获取 |
| `MessageUtilsTest` | 便捷方法验证、消息转换 |

### 4.2 测试示例

#### 文件: `open-sagent-core/src/test/java/ai/sagesource/opensagent/core/llm/message/CompletionMessageTest.java`

```java
package ai.sagesource.opensagent.core.llm.message;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 消息模型单元测试
 *
 * @author: sage.xue
 * @time: 2026/4/13
 */
class CompletionMessageTest {

    @Test
    @DisplayName("创建用户文本消息 - 成功")
    void testCreateUserMessage() {
        UserMessage message = UserMessage.of("你好");

        assertNotNull(message);
        assertEquals(MessageRole.USER, message.getRole());
        assertEquals("你好", message.getTextContent());
    }

    @Test
    @DisplayName("创建系统消息 - 成功")
    void testCreateSystemMessage() {
        SystemMessage message = SystemMessage.of("你是一个助手");

        assertNotNull(message);
        assertEquals(MessageRole.SYSTEM, message.getRole());
        assertEquals("你是一个助手", message.getTextContent());
    }

    @Test
    @DisplayName("创建助手消息 - 成功")
    void testCreateAssistantMessage() {
        AssistantMessage message = AssistantMessage.of("很高兴为你服务");

        assertNotNull(message);
        assertEquals(MessageRole.ASSISTANT, message.getRole());
        assertEquals("很高兴为你服务", message.getTextContent());
    }

    @Test
    @DisplayName("创建多模态用户消息 - 成功")
    void testCreateMultimodalUserMessage() {
        UserMessage message = UserMessage.builder()
                .contents(List.of(
                        TextContent.builder().text("这是什么图片？").build(),
                        ImageContent.builder().url("https://example.com/image.png").build()
                ))
                .build();

        assertNotNull(message);
        assertEquals(2, message.getContents().size());
        assertEquals(ContentType.TEXT, message.getContents().get(0).getType());
        assertEquals(ContentType.IMAGE, message.getContents().get(1).getType());
    }

    @Test
    @DisplayName("使用MessageUtils创建消息 - 成功")
    void testMessageUtils() {
        CompletionMessage userMsg = MessageUtils.user("用户问题");
        CompletionMessage sysMsg = MessageUtils.system("系统提示");
        CompletionMessage assistantMsg = MessageUtils.assistant("助手回复");

        assertEquals(MessageRole.USER, userMsg.getRole());
        assertEquals(MessageRole.SYSTEM, sysMsg.getRole());
        assertEquals(MessageRole.ASSISTANT, assistantMsg.getRole());
    }

    @Test
    @DisplayName("添加内容到消息 - 成功")
    void testAddContent() {
        UserMessage message = UserMessage.of("初始内容");
        message.addText("追加内容");

        assertEquals(2, message.getContents().size());
        assertTrue(message.getTextContent().contains("初始内容"));
        assertTrue(message.getTextContent().contains("追加内容"));
    }

    @Test
    @DisplayName("图片内容获取数据源 - URL优先")
    void testImageContentSource() {
        ImageContent imageWithUrl = ImageContent.builder()
                .url("https://example.com/image.png")
                .base64Data("base64data")
                .build();

        assertEquals("https://example.com/image.png", imageWithUrl.getImageSource());
    }

    @Test
    @DisplayName("图片内容获取数据源 - BASE64回退")
    void testImageContentBase64Source() {
        ImageContent imageWithBase64 = ImageContent.builder()
                .base64Data("iVBORw0KGgo=")
                .mimeType("image/png")
                .build();

        assertEquals("data:image/png;base64,iVBORw0KGgo=", imageWithBase64.getImageSource());
    }

    @Test
    @DisplayName("工具消息创建 - 成功")
    void testToolMessage() {
        ToolMessage message = ToolMessage.of("call_123", "calculator", "42");

        assertNotNull(message);
        assertEquals(MessageRole.TOOL, message.getRole());
        assertEquals("call_123", message.getToolCallId());
        assertEquals("calculator", message.getToolName());
        assertEquals("42", message.getTextContent());
    }
}
```

## 5. 方案变更记录

| 评审人 | 时间 | 结论 | 备注 |
|--------|------|------|------|
| | | | |
