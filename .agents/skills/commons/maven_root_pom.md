---
name: maven-root-pom
description: MAVEN根POM.XML文件生成规范
---

当需要生成工程根pom.xml文件时，工程使用maven多模块构建

根pom.xml负责统一管理依赖版本

工程模块示例:

```
根目录/
├── pom.xml                    # 父POM，统一管理依赖版本
├── 模块1
├── 模块2
```

pom文件说明

- <version>${revision}</version>
- <revision>1.0.0-SNAPSHOT</revision>
- <maven.compiler.source>17</maven.compiler.source>
- <maven.compiler.target>17</maven.compiler.target>
- <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
- plugins
```xml
<build>
        <pluginManagement>
            <plugins>
                <!-- Spring Boot构建配置 -->
                <plugin>
                    <groupId>org.springframework.boot</groupId>
                    <artifactId>spring-boot-maven-plugin</artifactId>
                    <version>${spring-boot.version}</version>
                </plugin>

                <!-- 配置 flatten-maven-plugin -->
                <plugin>
                    <groupId>org.codehaus.mojo</groupId>
                    <artifactId>flatten-maven-plugin</artifactId>
                    <version>1.5.0</version> <!-- 建议使用较新版本 -->
                    <configuration>
                        <updatePomFile>true</updatePomFile> <!-- 关键：更新原始POM文件 -->
                        <flattenMode>resolveCiFriendliesOnly</flattenMode>
                    </configuration>
                    <executions>
                        <execution>
                            <id>flatten</id>
                            <phase>process-resources</phase>
                            <goals>
                                <goal>flatten</goal>
                            </goals>
                        </execution>
                        <execution>
                            <id>flatten.clean</id>
                            <phase>clean</phase>
                            <goals>
                                <goal>clean</goal>
                            </goals>
                        </execution>
                    </executions>
                </plugin>
            </plugins>
        </pluginManagement>
    </build>
```