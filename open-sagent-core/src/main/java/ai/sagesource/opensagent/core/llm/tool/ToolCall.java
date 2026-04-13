package ai.sagesource.opensagent.core.llm.tool;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Tool调用请求
 * <p>
 * LLM返回的待执行工具调用信息，包含调用ID、工具名称和参数
 *
 * @author: sage.xue
 * @time: 2026/4/13
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolCall {

    /**
     * 工具调用唯一ID
     */
    private String id;

    /**
     * 工具名称
     */
    private String name;

    /**
     * 调用参数（JSON解析后的Map）
     */
    private Map<String, Object> arguments;
}
