---
name: project-exception-system
description: 项目异常体系说明
---

## 异常体系说明

```
OpenSagentException
├── OpenSagentLLMException  # LLM相关异常
```

所有异常都需要继承OpenSagentException基类，基类定义在base模块

各个模块的异常，在模块内定义即可