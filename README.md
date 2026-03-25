# WeTalk 💬

WeTalk 是一个基于 `Spring Boot + MyBatis + Redis + Netty` 的仿微信聊天后端项目，支持用户、联系人、群聊、文件、WebSocket 实时消息以及部分 AI 能力扩展。

当前仓库版本：`0.0.1`

## ✨ 项目特性

- 用户注册、登录、退出
- 验证码登录与基础账号安全控制
- 联系人管理、好友申请、黑名单
- 群聊管理与群成员相关操作
- WebSocket 实时通信
- 文件上传与资源管理
- Redis 缓存与会话辅助
- MySQL 持久化存储
- Spring AI 扩展能力接入
- 后台管理相关接口

## 🧰 技术栈

- Java 17
- Spring Boot 3.4.5
- MyBatis 3.0.4
- MySQL
- Redis
- Netty
- Redisson
- Spring AI
- Lombok
- Hutool

## 📦 运行环境

- JDK 17
- Maven 3.9+ 或使用项目自带 `mvnw`
- MySQL 8.x
- Redis 5.x 及以上

## 🚀 快速启动

### 1. 准备数据库

- 创建名为 `easychat` 的数据库
- 导入项目对应的表结构和初始数据

### 2. 修改配置

请检查 `src/main/resources/application.yml` 中的以下配置：

- `spring.datasource.url`
- `spring.datasource.username`
- `spring.datasource.password`
- `spring.data.redis.host`
- `spring.data.redis.port`
- `spring.ai.openai.api-key`
- `project.folder`

### 3. 启动项目

使用 Maven Wrapper 启动：

```bash
./mvnw spring-boot:run
```

Windows 下也可以使用：

```bat
mvnw.cmd spring-boot:run
```

如果你希望先打包再运行：

```bash
./mvnw clean package -DskipTests
java -jar target/WeTalk-0.0.1-SNAPSHOT.jar
```

## 🌐 运行信息

- HTTP 端口：`5050`
- 项目上下文路径：`/api`
- WebSocket 端口：`5051`

## 🗂️ 目录说明

- `src/main/java`：Java 源码
- `src/main/resources`：配置文件、MyBatis 映射文件、静态资源
- `src/test/java`：测试代码
- `file`：项目文件存储目录
- `pom.xml`：Maven 构建文件

## 🔧 主要模块

- `controller`：接口层
- `service`：业务层
- `mappers`：MyBatis 接口
- `entity`：实体类
- `config`：配置类
- `component`：通用组件
- `webSocket`：WebSocket 与 Netty 相关逻辑
- `utils`：工具类
- `aspect`：切面处理

## 📝 说明

- 这是项目的 `0.0.1` 初始整理版本。
- 如果你后续准备对外公开，建议把 `application.yml` 中的敏感信息改为环境变量或本地私有配置。

## 🤝 贡献

- 欢迎提交 Issue 和 Pull Request
- 如果你发现配置、文档或接口描述有不一致的地方，也欢迎一起完善

