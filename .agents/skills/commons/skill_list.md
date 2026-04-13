---
name: skill-list
description: 生成SKILL列表
---

## 列表生成规范

扫描 .agent 路径下：skills/commons, skills/infrastructure, skills/references 的SKILL列表输出到 .agent/SKILL.md文件

> skills/commons: 公共类SKILLS
> 
> skills/infrastructure: 架构方案类SKILLS
> 
> skills/references: 引用类SKILLS

文件格式：

```markdown

# SKILLS列表

## 公共类SKILLS

| SKILL名称 | SKILL路径 | SKILL描述 |
|---------|---------|---------|

## 架构方案类SKILLS

| SKILL名称 | SKILL路径 | SKILL描述 |
|---------|---------|---------|

## 引用类SKILLS

| SKILL名称 | SKILL路径 | SKILL描述 |
|---------|---------|---------|

```

## 其他约束

> 如果一个已经存在的SKILL的描述有变化，也需要更新