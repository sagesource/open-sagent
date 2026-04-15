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
