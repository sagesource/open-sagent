---
name: project-design-web
description: Agent Web模块实现
---

## Web模块实现方案

1. 支持注册(邮箱注册，无需发送验证码)、登录、修改个人信息(当前用户的对话昵称、密码)
2. Web API接口必须支持登录验证，登录有效期可配置为24H
3. 支持对话列表、Agent版本选择
   - 对话列表使用SQLLite存储
   - Sagent-Simple: 使用SimpleAgent，不支持工具调用，适合简单任务
   - Sagent-Smart：使用ReActAgent实现
4. 无论任何一个Agent版本，除了需要必须得Agent实现，还需要基于SimpleAgent实现一个生成对话标题的Agent
   - 如果是第一条对话 OR 对话历史 < 5 条，需要调用标题Agent生成标题&持久化
5. 每一个Agent都要使用独立的LLM配置项
6. 全部使用MultipleSQLLiteMemory作为Memory组件
7. 后端使用Spring-Boot，初始化Agent所需的参数均支持从Spring配置文件中获取；配置文件的值，支持使用DotEnv获取.env的配置
8. Agent需要使用Prompt组件从指定的路径加载提示词，提示词路径、占位符值替换，都支持在配置文件中设置

## Web页面设计说明

1. 支持暗黑模式和明亮模式切换，对话列表支持修改标题、删除操作；对话支持中断
2. 使用TypeScript开发，前后端分离
3. 需要在plan方案中给出如何运行前端的方案
4. 和Agent的交互使用支持SSE的流式交互，一轮对话完成后需要刷新对话(目的是获取最新的标题)
5. 支持响应的Markdown语法、代码块语法（要支持显示语言、复制）
6. 针对后端对话接口返回特殊的AGENT_ACTION动作，需要直接在页面输出类似于：正在XXXXX
7. 首页为登录注册页面，登录后进入对话页面

## 其他说明

1. 需要将后端接口生成为SKILL，便于后续开发过程进行参考
2. 开发SSE流式对话接口，需要完善处理网络问题
3. 异常处理：后端服务异常需要包装为JSON返回给前端，前端展示异常JSON