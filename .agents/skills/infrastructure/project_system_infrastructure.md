---
name: project-system-infrastructure
description: 项目系统架构说明：包括工程模块架构
---

```
open-sagent/
├── open-sagent-base    基础模块：工具类、自定义异常基类
├── open-sagent-core    核心模块：LLM、Agent、记忆、上下文等核心定义，不依赖Spring容器等具体技术实现
├── open-sagent-web     WEB-AGENT实现模块
├── open-sagent-cli     CLI-AGENT实现模块
├── open-sagent-tools   AGENT TOOL实现
├── open-sagent-infrastructure  基础设施层：针对Core模块的抽象提供具体实现，例如对接不同LLM厂商接口的实现
├── open-sagent-example 示例代码模块
```