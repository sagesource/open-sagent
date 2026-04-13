---
name: project-design-llm
description: 项目系统设计方案，LLM相关
---

## LLM模块设计方案

### 模块功能概述

1. 抽象封装LLM大模型交互，与厂商实现无关
2. 核心模型说明

| 模型         | 功能描述                                         |
|------------|----------------------------------------------|
| Client     | LLM客户端抽象封装                                   |
| Completion | LLM对话补全抽象，支持同步、异步、流式交互并支持工具调用                |
| Message    | LLM对话消息抽象，包括 System、Assist、User、Tool等LLM消息角色 |
| Memory     | 记忆抽象                                         |
| Agent      | AGENT模型，提供SimpleAgent和ReActAgent两种模式         |

### 模块流程描述

#### Client模块

该模块主要是对不同厂商的LLM Client进行封装，在[open-sagent-infrastructure]工程中依赖不同厂商的SDK实现Client

Client构建以参数化对象实现，在创建过程中如果发生异常，需要抛出```OpenSagentLLMException```

Client模型后续要提供给Completion模型完成对话

#### Message模块

该模块主要封装请求大模型的消息和大模型响应的消息

基类：CompletionMessage

Message类型：SYSTEM ASSISTANT DEVELOPER TOOL USER

Message应该支持 文本 + 多文件，文件需要支持BASE64 或 URI 引用

#### 