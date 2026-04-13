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
