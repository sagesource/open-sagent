---
name: agent-action-markers
description: Agent流式输出中固定返回的AGENT_ACTION[*]动作标记清单，供客户端识别并执行不同展示逻辑
type: reference
---

## AGENT_ACTION 标记规范

Agent在流式(`stream`)模式下，通过向 consumer 发送固定格式的文本标记，通知客户端当前正在执行的特殊动作。客户端根据这些标记渲染对应的UI状态（如"工具执行中"、"记忆压缩中"等）。

### 标记格式

```
AGENT_ACTION[<动作标识>]
```

- 固定前缀：`AGENT_ACTION[`
- 动作标识：大写下划线分隔的英文标识
- 固定后缀：`]`

---

## 动作标记清单

| 动作标记 | 触发场景 | 发送时机 | 客户端展示建议 |
|----------|----------|----------|----------------|
| `AGENT_ACTION[EXECUTE_TOOL]` | ReActAgent流式模式下执行工具调用 | 本轮流式输出结束、检测到工具调用后、实际执行工具前 | 显示"工具执行中"状态/loading |
| `AGENT_ACTION[EXECUTE_MEMORY_COMPORESS]` | Memory判断需要压缩并执行压缩时 | 调用Memory.compress()判断需要压缩后、执行压缩前 | 显示"记忆整理中"状态/loading |

---

## 代码中的定义位置

### `AGENT_ACTION[EXECUTE_TOOL]`

- **架构方案**：`project_design_reactagent.md` 第9条
- **Plan方案**：`010-Agent模块-ReActAgent设计方案.md` 第1.2节第8条、2.3节文件4
- **实现代码**：`ReActAgent.java` 常量 `AGENT_ACTION_EXECUTE_TOOL`
- **发送逻辑**：`ReActAgent.doReActStreamLoop()` 方法中，本轮流式结束后、工具执行前

```java
// ReActAgent.java 发送示例
consumer.accept(StreamChunk.builder()
        .deltaText(AGENT_ACTION_EXECUTE_TOOL)
        .aggregatedText(AGENT_ACTION_EXECUTE_TOOL)
        .build());
```

### `AGENT_ACTION[EXECUTE_MEMORY_COMPORESS]`

- **架构方案**：`project_design_reactagent.md` 第10条
- **触发流程**：
  1. Agent调用 `memory.shouldCompress()` 判断是否需要压缩
  2. 返回 `true` 时，发送 `AGENT_ACTION[EXECUTE_MEMORY_COMPORESS]` 标记
  3. 执行 `memory.compress()` 完成压缩

> **状态说明**：该标记在架构方案中已定义，但具体实现需结合 Memory 的 `shouldCompress()` + `compress()` 流程，在 Agent 执行前发送标记。

---

## 扩展规范

如需新增 AGENT_ACTION 标记：

1. 遵循 `AGENT_ACTION[<大写下划线标识>]` 格式
2. 在对应的架构方案 SKILL 中记录触发场景和发送时机
3. 在 Plan 方案中明确客户端展示建议
4. 更新本 SKILL 清单
