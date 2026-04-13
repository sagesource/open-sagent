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
