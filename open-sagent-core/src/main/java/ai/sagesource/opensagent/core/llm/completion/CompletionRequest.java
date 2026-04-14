package ai.sagesource.opensagent.core.llm.completion;

import ai.sagesource.opensagent.core.llm.message.CompletionMessage;
import ai.sagesource.opensagent.core.llm.tool.ToolDefinition;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 补全请求参数
 * <p>
 * 封装调用大模型进行对话补全所需的请求参数
 *
 * @author: sage.xue
 * @time: 2026/4/14
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompletionRequest {

    /**
     * 对话消息列表
     */
    @Builder.Default
    private List<CompletionMessage> messages = new ArrayList<>();

    /**
     * 工具定义列表（可选）
     */
    @Builder.Default
    private List<ToolDefinition> tools = new ArrayList<>();

    /**
     * 采样温度（0-2）
     */
    private Double temperature;

    /**
     * 最大生成Token数
     */
    private Integer maxTokens;

    /**
     * 是否流式输出
     */
    @Builder.Default
    private boolean stream = false;

    /**
     * 添加消息（便捷方法）
     *
     * @param message 消息
     */
    public void addMessage(CompletionMessage message) {
        if (this.messages == null) {
            this.messages = new ArrayList<>();
        }
        this.messages.add(message);
    }

    /**
     * 添加工具定义（便捷方法）
     *
     * @param tool 工具定义
     */
    public void addTool(ToolDefinition tool) {
        if (this.tools == null) {
            this.tools = new ArrayList<>();
        }
        this.tools.add(tool);
    }
}
