package ai.sagesource.opensagent.core.llm.tool;

import ai.sagesource.opensagent.core.llm.exception.OpenSagentToolException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工具注册表
 * <p>
 * 管理所有可用工具的注册与发现
 *
 * @author: sage.xue
 * @time: 2026/4/13
 */
public class ToolRegistry {

    private final Map<String, Tool> tools = new LinkedHashMap<>();

    /**
     * 注册工具
     *
     * @param tool 工具实例
     */
    public void register(Tool tool) {
        if (tool == null || tool.getDefinition() == null) {
            throw new OpenSagentToolException("Tool或其Definition不能为空");
        }
        String name = tool.getDefinition().getName();
        if (tools.containsKey(name)) {
            throw new OpenSagentToolException("工具已存在: " + name);
        }
        tools.put(name, tool);
    }

    /**
     * 批量注册工具
     *
     * @param tools 工具实例列表
     */
    public void registerAll(List<Tool> tools) {
        if (tools != null) {
            for (Tool tool : tools) {
                register(tool);
            }
        }
    }

    /**
     * 根据名称获取工具
     *
     * @param name 工具名称
     * @return Tool实例
     * @throws OpenSagentToolException 工具不存在时抛出
     */
    public Tool get(String name) {
        Tool tool = tools.get(name);
        if (tool == null) {
            throw new OpenSagentToolException("工具不存在: " + name);
        }
        return tool;
    }

    /**
     * 检查工具是否存在
     *
     * @param name 工具名称
     * @return true表示存在
     */
    public boolean contains(String name) {
        return tools.containsKey(name);
    }

    /**
     * 获取所有工具定义
     *
     * @return ToolDefinition列表
     */
    public List<ToolDefinition> getAllDefinitions() {
        List<ToolDefinition> definitions = new ArrayList<>();
        for (Tool tool : tools.values()) {
            definitions.add(tool.getDefinition());
        }
        return definitions;
    }

    /**
     * 获取已注册的所有工具
     *
     * @return Tool列表
     */
    public List<Tool> getAllTools() {
        return new ArrayList<>(tools.values());
    }

    /**
     * 清空注册表
     */
    public void clear() {
        tools.clear();
    }
}
