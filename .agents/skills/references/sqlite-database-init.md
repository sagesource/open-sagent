---
name: sqlite-database-init
description: 工程运行前预先创建 SQLite 数据库文件并初始化建表语句的规范与示例
type: reference
---

# SQLite 数据库预创建与初始化

## 核心原则

SQLite 数据库文件和表结构**在工程运行前预先创建**，代码中只负责数据操作，不再包含任何 `CREATE TABLE` 逻辑。

## 预先创建方式

### 方式一：命令行执行（推荐）

使用 SQLite 命令行工具直接执行脚本：

```bash
# 创建/打开数据库文件并执行建表脚本
sqlite3 memory.db < scripts/init-schema.sql

# 验证表是否创建成功
sqlite3 memory.db ".tables"
```

### 方式二：Maven 构建阶段自动初始化

配置 `exec-maven-plugin` 在编译阶段执行 SQL：

```xml
<plugin>
    <groupId>org.codehaus.mojo</groupId>
    <artifactId>exec-maven-plugin</artifactId>
    <version>3.1.1</version>
    <executions>
        <execution>
            <id>init-sqlite-db</id>
            <phase>process-resources</phase>
            <goals><goal>exec</goal></goals>
            <configuration>
                <executable>sqlite3</executable>
                <arguments>
                    <argument>${project.basedir}/memory.db</argument>
                    <argument>.read</argument>
                    <argument>${project.basedir}/scripts/init-schema.sql</argument>
                </arguments>
            </configuration>
        </execution>
    </executions>
</plugin>
```

### 方式三：Java 启动脚本初始化

在 `main()` 方法或 `@PostConstruct` 中执行一次性初始化（仅首次）：

```java
@PostConstruct
public void initDatabase() {
    try (Connection conn = DriverManager.getConnection("jdbc:sqlite:memory.db");
         Statement stmt = conn.createStatement()) {
        // 读取 scripts/init-schema.sql 内容并执行
        String sql = Files.readString(Path.of("scripts/init-schema.sql"));
        stmt.execute(sql);
    }
}
```

### 方式四：Docker 启动时挂载

```dockerfile
COPY scripts/init-schema.sql /app/scripts/
RUN sqlite3 /app/memory.db < /app/scripts/init-schema.sql
```

## Maven 依赖

```xml
<dependency>
    <groupId>org.xerial</groupId>
    <artifactId>sqlite-jdbc</artifactId>
    <version>3.45.0.0</version>
</dependency>
```

## JDBC 连接

```java
// 数据库文件不存在时会自动创建空文件，但表需预先初始化
Connection conn = DriverManager.getConnection("jdbc:sqlite:memory.db");
```

## 注意事项

- `IF NOT EXISTS` 可确保多次执行脚本不会报错
- Web 模块表由 JPA 管理，是否需要手动执行取决于 `spring.jpa.hibernate.ddl-auto` 配置
- Memory 模块的 `sessions` 表中的 session 记录需由调用方预先插入，代码中不再自动创建
