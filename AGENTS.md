# Open Sagent AI-Framework

> 全程使用中文输出
> 无论是否为plan模式，必须遵守如下约定

## 核心工作流程说明

> 接受任务指令 -> 判断是新建还是变更已有的.agent/plan下的设计方案 -> 在SKILLS中寻找架构方案和必要SKILL -> 生成新方案OR变更已有方案
> -> 等待方案评审结果 -> 通过: 进入变更实施 \ 不通过：重新接受任务指令

## SKILLS说明

### SKILLS分类

> 公共类SKILL、架构方案SKILL、引用类SKILL
> 
> [.agent/skills/SKILLS.md](.agents/skills/SKILLS.md) 为SKILLS列表文件,当你需要检索SKILL时可以优先读取该文件

- 工程工作目录 [.agent/skills](.agents/skills) 路径为Skills目录入口
- [.agent/skills/commons](.agents/skills/commons) 根路径下为公共类SKILLS，涉及编码规范、技术规范、变更约束、SKILL生成规范等
- [.agent/skills/infrastructure](.agents/skills/infrastructure) 路径下为系统架构和业务架构方案类SKILL
- [.agent/skills/references](.agents/skills/references) 路径下为引用类SKILL(编码示例，状态机描述等)

### SKILLS自动生成约定

> 只允许生成引用类SKILL，需要和我确认生成方案后，才可执行

- 引用类SKILL：当你认为生成的代码或功能需要增加编码示例SKILL时，参考SKILL生成规范生成对应的SKILL文件

### [.agent/skills/SKILLS.md](.agents/skills/SKILLS.md) 为SKILLS列表文件自动更新说明

- 自动生成新的SKILL文件后，更新该文件
- 当你使用了一个SKILL之后，检查该文件中是否存在；如果不存在，则更新该文件

## 修改与变更说明

> 所有涉及方案和代码的变更必须遵循评审流程。**严禁未经评审直接修改任何工程代码文件。**
> 
> 架构方案和Plan方案是不同的，当我提出新建和修改方案时，需要先在SKILL中寻找架构方案
> 
> 所有的修改与变更，都要先生成待评审的方案；必须得到我评审通过反馈，同时更新评审记录后再实施
