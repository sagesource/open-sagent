# SKILLS列表

## 公共类SKILLS

| SKILL名称                   | SKILL路径                              | SKILL描述                                   |
|---------------------------|--------------------------------------|-------------------------------------------|
| skill-list                | commons/skill_list.md                | 生成SKILL列表                                 |
| reference-skill-generator | commons/reference_skill_generator.md | 引用类SKILL生成规范                              |
| maven-root-pom            | commons/maven_root_pom.md            | MAVEN根POM.XML文件生成规范                       |
| technology-stack          | commons/technology_stack.md          | 技术栈列表、编码规范、模块依赖规范                         |
| modify-rules              | commons/modify_rules.md              | 修改与变更约束规范，包括代码变更和方案变更，以及变更通过、未通过、需要修改时的规定 |
| project-init              | commons/project_init.md              | 工程初始化生成规范                                 |

## 架构方案类SKILLS

| SKILL名称                        | SKILL路径                                          | SKILL描述                           |
|--------------------------------|--------------------------------------------------|-----------------------------------|
| project-system-infrastructure  | infrastructure/project_system_infrastructure.md  | 项目系统架构说明：包括工程模块架构                 |
| project-design-llm             | infrastructure/project_design_llm.md             | 项目系统设计方案，LLM相关                    |
| project-design-agent           | infrastructure/project_design_agent.md           | 项目系统设计方案，Agent核心相关              |
| project-design-reactagent      | infrastructure/project_design_reactagent.md      | 项目系统设计方案，ReActAgent相关             |
| project-design-tools-functions | infrastructure/project_design_tools_functions.md | Agent TOOLS开发方案，每一个Agent的Tool实现设计 |
| project-design-web             | infrastructure/project_design_web.md             | Agent Web模块实现                                |

## 方案类PLANS

| 方案编号 | 方案路径                                      | 方案描述                                          |
|------|-------------------------------------------|-----------------------------------------------|
| 001  | plan/001-LLM模块-Client设计方案.md              | LLM客户端模块设计方案                                  |
| 002  | plan/002-LLM模块-Message设计方案.md             | LLM消息模块设计方案                                   |
| 003  | plan/003-LLM模块-Tool设计方案.md                | LLM工具模块设计方案                                   |
| 004  | plan/004-LLM模块-Completion设计方案.md          | LLM对话补全模块设计方案                                 |
| 005  | plan/005-Agent模块-Prompt设计方案.md            | Agent-Prompt模块设计方案                            |
| 006  | plan/006-LLM厂商模块拆分-OpenAI基础设施层独立模块设计方案.md | LLM厂商模块拆分，将OpenAI实现独立为infrastructure-openai模块 |
| 007  | plan/007-Agent模块-异常包重构.md                 | Agent异常包重构设计方案                                |
| 008  | plan/008-Agent模块-Memory设计方案.md            | Agent-Memory模块设计方案                            |
| 009  | plan/009-Agent模块-Agent设计方案.md             | Agent核心模块设计方案                               |
| 010  | plan/010-Agent模块-ReActAgent设计方案.md        | ReActAgent多轮推理Agent设计方案                      |
| 011  | plan/011-Agent模块-MultipleSQLLiteMemory设计方案.md | Agent-MultipleSQLLiteMemory持久化内存设计方案        |
| 012  | plan/012-Agent模块-Memory压缩判断能力设计方案.md  | Agent-Memory模块新增 shouldCompress 压缩判断能力设计方案 |
| 013  | plan/013-Web模块-设计方案.md                      | Web模块完整实现方案（用户系统、对话管理、SSE流式对话、前端） |
| 013-frontend | plan/013-Web模块-前端风格优化方案.md         | Web模块前端UI风格优化方案（科技感主题、动画特效、现代化设计） |
| 014  | plan/014-数据库初始化SQL与SQLite数据库创建SKILL设计方案.md | 数据库初始化SQL与SQLite数据库创建SKILL设计方案 |
| 015  | plan/015-SpringBean依赖注入规范重构方案.md         | SpringBean依赖注入规范重构方案（统一使用@Resource注解） |
| 016  | plan/016-Error日志输出规范优化方案.md               | Error日志输出规范优化方案 |
| 017  | plan/017-Web模块-Memory-dbPath支持配置文件读取.md   | Web模块-Memory-dbPath支持配置文件读取 |
| 018  | plan/018-Web模块-修复chat_message表未记录assistant消息.md | Web模块-修复chat_message表未记录assistant消息 |
| 019  | plan/019-Web模块-修复Markdown流式渲染异常.md | Web模块-修复对话页面Markdown流式渲染不一致问题 |
| 020  | plan/020-Web模块-对话Loading动画与快捷键发送消息.md | Web模块-对话Loading动画与Command/Ctrl+Enter快捷键发送消息 |

## 引用类SKILLS

| SKILL名称                  | SKILL路径                                | SKILL描述            |
|--------------------------|----------------------------------------|--------------------|
| project-exception-system | references/project_exception_system.md | 项目异常体系说明           |
| llm-tool-definition      | references/llm_tool_definition.md      | 如何定义一个Tool（基于注解方式） |
| agent-action-markers     | references/agent_action_markers.md     | Agent流式输出AGENT_ACTION[*]动作标记规范 |
| web-backend-api          | references/web_backend_api.md          | Web模块后端REST API接口规范（认证、对话管理、SSE流式对话） |
| sqlite-database-init     | references/sqlite-database-init.md     | SQLite数据库预创建与初始化规范（工程运行前预先建表） |

---

> **说明**：
> - 公共类SKILLS：涉及编码规范、技术规范、变更约束、SKILL生成规范等
> - 架构方案类SKILLS：系统架构和业务架构方案类SKILL
> - 引用类SKILLS：编码示例，状态机描述等
