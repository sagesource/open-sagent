package ai.sagesource.opensagent.core.agent.memory;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 记忆压缩结果
 * <p>
 *     封装记忆压缩操作的返回结果
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
