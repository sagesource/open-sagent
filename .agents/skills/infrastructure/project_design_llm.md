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
| Tool       | LLM TOOL-CALLING工具抽象（工具定义、工具执行）              |

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

#### Tool模块

该模块主要是针对LLM Tool-Calling 的工具定义进行封装，无论各种厂商对工具定义如何，都要通过该模块的Tool定义生成

基于注解的方式，定义工具名称、工具参数名称、参数数据类型、是否必填

同时，需要支持将大模型返回的Tool调用信息进行封装(方法名称、参数信息等)

使用枚举方式定义工具参数, 支持一个类重定义多个工具

方法注解：@Tool 定义工具名称
参数注解：@ToolParam 定义工具参数

在工具注册阶段，传入工具类的Class，解析所有被注解定义的Tool；生成对应的Define对象

在工具执行阶段，可以执行到对应的方法


#### Completion模块

该模块要完成与大模型的交互(即对话补全)的封装，在[open-sagent-infrastructure]工程中依赖不同厂商的Client完成大模型调用

需要支持同步调用、异步调用、同步流式调用、异步流式调用，同时支持大模型的tool-calling功能

要特别处理在流式调用的场景下，获取&解析大模型返回工具调用信息的处理

在流式和异步调用的场景下，需要支持中断功能