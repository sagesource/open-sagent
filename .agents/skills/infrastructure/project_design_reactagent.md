---
name: project-design-reactagent
description: 项目系统设计方案，ReActAgent相关
---

## ReActAgent模块设计方案

以SimpleAgent做为范式进行扩展，实现ReActAgent框架

1. 支持设定最大迭代上限
2. 如果模型返回调用工具：react_finish_answer 时，终止迭代(结束调用工具名称可配置，做为替换Prompt提示词占位符的值)
3. 在一次react的调用中，需要监控同一个工具调用是否超过阈值配置(该参数可配置)，如果超过阈值输出Log的Warn告警
4. 支持工具调用，将工具调用的结果做为ToolMessage送入下一次循环
5. 如果超过最大迭代次数仍然没有结束，抛出异常
6. 当工具调用失败，也将异常返回做为ToolMessage送给模型，由模型决定处理流程
7. 与SimpleAgent实现一致，支持Memory、中断、统计token用量
8. 全程使用流式输出，completion已经支持流式输出中最后返回工具调用信息；可以一边输出流式返回的文本，本轮迭代完成后根据返回的工具信息调用工具
9. 当执行工具时，callback中固定返回内容: AGENT_ACTION[EXECUTE_TOOL], 后续客户端会根据这个特殊的内容，执行不同的展示逻辑
10. 针对记忆压缩的处理：
    - 首先调用Memory模块判断是否需要压缩
    - 返回需要压缩：callback中固定返回内容：AGENT_ACTION[EXECUTE_MEMORY_COMPORESS],后续客户端会根据这个特殊的内容，执行不同的展示逻辑
    - 执行压缩逻辑