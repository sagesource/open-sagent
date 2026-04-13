package ai.sagesource.opensagent.core.llm.tool;

/**
 * 工具参数类型枚举
 * <p>
 * 对应JSON Schema的基础类型，用于描述Tool参数的Schema
 *
 * @author: sage.xue
 * @time: 2026/4/13
 */
public enum ToolParameterType {

    STRING,
    INTEGER,
    NUMBER,
    BOOLEAN,
    OBJECT,
    ARRAY
}
