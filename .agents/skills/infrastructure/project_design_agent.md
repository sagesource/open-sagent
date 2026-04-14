---
name: project-design-agent
description: 项目系统设计方案，Agent、Memory、Prompt相关
---

## Prompt模块设计方案

Prompt是Agent运行的关键部分，本模块主要是实现支持自定义&占位符替换的System Prompt的加载

Prompt支持从工程目录或者指定路径加载Prompt模板，然后对模板内的占位符{{占位符:默认值}}进行替换，使用方可以基于预置模板生成SYSTEM PROMPT

优先级：指定路径 > 工程目录

在core模块定义与prompt相关的核心模型与功能，在infrastructure模块完成具体实现（指定路径加载和指定工程目录加载）

## Memory模块设计方案

