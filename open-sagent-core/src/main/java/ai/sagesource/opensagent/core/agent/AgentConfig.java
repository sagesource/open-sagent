package ai.sagesource.opensagent.core.agent;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Agent配置参数
 * <p>
 * 封装Agent运行时的核心配置项，包括工具支持开关、循环次数、采样温度、最大Token数等。
 *
 * @author: sage.xue
 * @time: 2026/4/16
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentConfig {

    /**
     * 是否开启工具支持
     * <p>
     * 开启后，Agent在调用Completion时会传递已注册的工具定义；
     * 如果模型返回ToolCall，Agent将执行对应工具。
     */
    @Builder.Default
    private boolean enableTools = false;

    /**
     * 最大循环次数（可选）
     * <p>
     * 主要用于ReActAgent等多轮推理模式，SimpleAgent中可作为扩展预留。
     */
    private Integer maxIterations;

    /**
     * 采样温度（0-2）
     */
    private Double temperature;

    /**
     * 最大生成Token数
     */
    private Integer maxTokens;
}
