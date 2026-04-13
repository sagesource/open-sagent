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
    void testCreateUserCompletionMessage() {
        UserCompletionMessage message = UserCompletionMessage.of("你好");

        assertNotNull(message);
        assertEquals(MessageRole.USER, message.getRole());
        assertEquals("你好", message.getTextContent());
    }

    @Test
    @DisplayName("创建系统消息 - 成功")
    void testCreateSystemCompletionMessage() {
        SystemCompletionMessage message = SystemCompletionMessage.of("你是一个助手");

        assertNotNull(message);
        assertEquals(MessageRole.SYSTEM, message.getRole());
        assertEquals("你是一个助手", message.getTextContent());
    }

    @Test
    @DisplayName("创建助手消息 - 成功")
    void testCreateAssistantCompletionMessage() {
        AssistantCompletionMessage message = AssistantCompletionMessage.of("很高兴为你服务");

        assertNotNull(message);
        assertEquals(MessageRole.ASSISTANT, message.getRole());
        assertEquals("很高兴为你服务", message.getTextContent());
    }

    @Test
    @DisplayName("创建多模态用户消息 - 成功")
    void testCreateMultimodalUserCompletionMessage() {
        UserCompletionMessage message = UserCompletionMessage.builder()
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
        UserCompletionMessage message = UserCompletionMessage.of("初始内容");
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
    void testToolCompletionMessage() {
        ToolCompletionMessage message = ToolCompletionMessage.of("call_123", "calculator", "42");

        assertNotNull(message);
        assertEquals(MessageRole.TOOL, message.getRole());
        assertEquals("call_123", message.getToolCallId());
        assertEquals("calculator", message.getToolName());
        assertEquals("42", message.getTextContent());
    }
}
