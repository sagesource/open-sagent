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
| project-design-agent           | infrastructure/project_design_agent.md           | 项目系统设计方案，Agent、Memory、Prompt相关    |
| project-design-tools-functions | infrastructure/project_design_tools_functions.md | Agent TOOLS开发方案，每一个Agent的Tool实现设计 |

## 方案类PLANS

| 方案编号 | 方案路径                                      | 方案描述                                          |
|------|-------------------------------------------|-----------------------------------------------|
| 001  | plan/001-LLM模块-Client设计方案.md              | LLM客户端模块设计方案                                  |
| 002  | plan/002-LLM模块-Message设计方案.md             | LLM消息模块设计方案                                   |
| 003  | plan/003-LLM模块-Tool设计方案.md                | LLM工具模块设计方案                                   |
| 004  | plan/004-LLM模块-Completion设计方案.md          | LLM对话补全模块设计方案                                 |
| 005  | plan/005-Agent模块-Prompt设计方案.md            | Agent-Prompt模块设计方案                            |
| 006  | plan/006-LLM厂商模块拆分-OpenAI基础设施层独立模块设计方案.md | LLM厂商模块拆分，将OpenAI实现独立为infrastructure-openai模块 |
| 007  | plan/007-Agent模块-异常处理方案.md                | Agent异常处理方案                                   |
| 008  | plan/008-Agent模块-Memory设计方案.md            | Agent-Memory模块设计方案                            |
| 009  | plan/009-Agent模块-Agent设计方案.md             | Agent核心模块设计方案                               |

## 引用类SKILLS

| SKILL名称                  | SKILL路径                                | SKILL描述            |
|--------------------------|----------------------------------------|--------------------|
| project-exception-system | references/project_exception_system.md | 项目异常体系说明           |
| llm-tool-definition      | references/llm_tool_definition.md      | 如何定义一个Tool（基于注解方式） |

---

> **说明**：
> - 公共类SKILLS：涉及编码规范、技术规范、变更约束、SKILL生成规范等
> - 架构方案类SKILLS：系统架构和业务架构方案类SKILL
> - 引用类SKILLS：编码示例，状态机描述等
