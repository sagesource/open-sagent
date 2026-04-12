# Open Sagent AI-Framework

> 全程使用中文输出

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

### 修改与变更的分类

- 新建方案：当我提出了一个方案问题时
- 修改方案：针对已经生成plan的方案进行变更
- 代码与文件修改：当我直接提出要修改代码时

### 修改与变更约束

- 如果没有给出方案信息或者没有明确说明时新方案时，你需要先询问是变更已有方案还是生成新方案
- 如果我直接说明修改代码时，你需要询问我是否需要生成方案