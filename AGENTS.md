# Open Sagent AI-Framework

## SKILLS说明

### SKILLS分类

> 公共类SKILL、架构方案SKILL、引用类SKILL
> 
> [.agent/skills/SKILLS.md](.agents/skills/SKILLS.md) 为SKILLS列表文件

- 工程工作目录 [.agent/skills](.agents/skills) 路径为Skills目录入口
- [.agent/skills/commons](.agents/skills/commons) 根路径下为公共类SKILLS，涉及编码规范、技术规范、变更约束、SKILL生成规范等
- [.agent/skills/infrastructure](.agents/skills/infrastructure) 路径下为系统架构和业务架构方案类SKILL
- [.agent/skills/references](.agents/skills/references) 路径下为引用类SKILL(编码示例，状态机描述等)

### SKILLS自动生成约定

> 只允许生成引用类SKILL，需要和我确认生成方案后，才可执行

- 引用类SKILL：当你认为生成的代码或功能需要增加编码示例SKILL时，参考SKILL生成规范生成对应的SKILL文件

### [.agent/skills/SKILLS.md](.agents/skills/SKILLS.md) 为SKILLS列表文件变更说明

- 自动生成新的SKILL文件后，更新该文件