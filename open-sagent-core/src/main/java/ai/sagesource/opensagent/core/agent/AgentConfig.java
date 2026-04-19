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

    /**
     * 结束工具名称
     * <p>
     * 用于ReActAgent模式，当模型调用该名称的工具时，表示任务已完成，终止迭代循环。
     * 该值会作为Prompt模板中占位符的替换值，提示模型在任务完成时调用此工具。
     */
    @Builder.Default
    private String finishToolName = "react_finish_answer";

    /**
     * 同一工具调用阈值
     * <p>
     * 用于ReActAgent模式，在一次ReAct调用中，如果同一个工具被调用的次数超过该阈值，
     * 输出Warn级别日志告警，防止模型陷入无限循环或重复调用。
     * 默认为3次。
     */
    @Builder.Default
    private Integer toolCallThreshold = 3;
}
