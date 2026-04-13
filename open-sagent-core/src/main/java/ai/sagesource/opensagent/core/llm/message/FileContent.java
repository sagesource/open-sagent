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
