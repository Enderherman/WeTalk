# WeTalk 后端

WeTalk 为[桌面客户端](https://github.com/Enderherman/WeTalkApp)和 [Web 客户端](https://github.com/Enderherman/WeTalkWeb)提供统一聊天服务。当前 Maven 版本为 `0.0.3`，技术栈为 Java 17、Spring Boot 3.4.5、MyBatis、MySQL、Redis/Redisson 和 Netty WebSocket。

当前源码包含账号与邮箱注册、好友申请与拉黑、私有备注、群管理、历史消息、未读与已读游标、附件与媒体读取、登录设备管理、管理员配置和版本发布。AI 文字聊天通过 OpenAI 兼容接口接入，默认关闭。

生产域名：留空，待确定。本轮功能与文档改动尚未部署 NAS；历史部署记录统一保存在 [CHANGELOG](CHANGELOG.md)，不能作为当前线上版本证明。

## 本地启动

准备 JDK 17 或兼容的新版本、Maven 3.9+、MySQL 8 和 Redis。项目没有 Maven Wrapper。数据库默认名为 `wetalk`，文件目录必须可写并持久保留。

1. 根据 [SQL 说明](sql/README.md)初始化空库，或为已有库执行尚未应用的迁移。
2. 参照 [.env.example](.env.example)设置数据库、Redis 和文件目录。直接运行 Java 时须向进程提供环境变量；Spring Boot 不会自动加载本地 `.env`。
3. 在仓库根目录构建并启动：

```sh
mvn -B -ntp clean verify
java -jar target/wetalk.jar
```

默认 HTTP 地址为 `http://127.0.0.1:5050/api`，WebSocket 地址为 `ws://127.0.0.1:5051/ws`。启动依赖 MySQL 和 Redis；AI 关闭时不需要 API Key。邮件默认关闭，此时已有账号可登录，但注册邮箱验证码服务不可用。

```sh
curl --fail http://127.0.0.1:5050/api/actuator/health/readiness
```

就绪检查同时检查应用、数据库、Redis 和 WebSocket 绑定状态；健康接口不公开内部详情。`UP` 只表示服务就绪，不能替代注册、聊天或文件收发验收。

## 运行配置

配置来源为 [application.yml](src/main/resources/application.yml)；`docker` profile 的文件目录默认为 `/data/wetalk/`。下表列出主要变量，真实凭据仅放在私有运行环境中。

| 变量 | 默认值与用途 |
|---|---|
| `DB_URL` | 本机 MySQL `3306/wetalk`；使用实际 JDBC 地址 |
| `DB_USERNAME` / `DB_PASSWORD` | `wetalk` / 空值；配置专用账号及密码 |
| `DB_POOL_SIZE` | `10` |
| `REDIS_HOST` / `REDIS_PORT` / `REDIS_DATABASE` | `127.0.0.1` / `6379` / `0` |
| `REDIS_USERNAME` / `REDIS_PASSWORD` / `REDIS_SSL` | 可选 ACL 用户、密码；TLS 默认 `false` |
| `PROJECT_FOLDER` | `./data/`；使用可写目录并保留末尾 `/` |
| `HTTP_PORT` / `WS_PORT` | `5050` / `5051` |
| `SPRING_PROFILES_ACTIVE` | `dev`；Compose 使用 `docker` |
| `ADMIN_EMAILS` | 空；仅填写已核实身份的现有管理员邮箱，逗号分隔 |
| `MAX_UPLOAD_SIZE` | `500MB`；同时限制单文件和整个 multipart 请求 |
| `WETALK_WEB_ALLOWED_ORIGINS` | 本地允许 `http://localhost:5173`、`http://127.0.0.1:5173`；部署时换成实际网页 Origin |
| `WETALK_WEB_AUTH_COOKIE_SECURE` | `false`；HTTPS 部署设为 `true` |
| `WETALK_EMAIL_ENABLED` | `false`；注册需启用并配置 SMTP |
| `MAIL_HOST` / `MAIL_PORT` / `MAIL_PROTOCOL` | `smtp.qq.com` / `465` / `smtps` |
| `MAIL_USERNAME` / `MAIL_PASSWORD` | 空；发件账号和 SMTP 凭据 |
| `MAIL_DEBUG` | `false`；保持关闭，避免邮件信息进入调试日志 |
| `MAIL_CONNECTION_TIMEOUT` / `MAIL_READ_TIMEOUT` / `MAIL_WRITE_TIMEOUT` | `5000` / `10000` / `10000` 毫秒，同时适用于 SMTP 与 SMTPS |
| `WETALK_AI_ENABLED` / `WETALK_AI_MODEL` | `false` / `none`；启用文字 AI 时改为 `true` / `openai` |
| `OPENAI_BASE_URL` / `OPENAI_MODEL` | 默认 `https://api.deepseek.com` / `deepseek-flash`；按提供方实际能力配置 |
| `DEEPSEEK_API_KEY` / `OPENAI_API_KEY` | 前者优先，后者兼容；AI 关闭时可留空 |

Spring Redis 和 Redisson 使用相同的地址、认证、数据库及 TLS 配置。不同 WeTalk 实例应隔离 Redis 数据库和文件目录。容器中的 `127.0.0.1` 指容器自身；跨容器连接使用网络内服务名。

管理员邮箱不能通过公开注册创建。先通过可信流程建立并核实账号，再配置 `ADMIN_EMAILS`；普通资料接口只接受昵称、性别、签名、地区和好友加入方式。

## 客户端接入约定

API 以 `/api` 为前缀，普通响应字段为 `status`、`code`、`message`、`data`。客户端需检查业务 `code`，不能只判断 HTTP 200；登录失效为 `901`，限流为 `429`。下载与媒体流接口直接返回文件字节。

| 能力 | 当前约定 |
|---|---|
| Web 登录 | `POST /api/account/webLogin` 写入 `wetalk_session` HttpOnly、SameSite Strict Cookie |
| 桌面登录 | `POST /api/account/login`；兼容 token 请求头与原生 WebSocket token 参数 |
| WebSocket | Web 先调用 `POST /api/account/webSocketTicket` 获取 60 秒一次性 ticket，再连接 `/ws?ticket=...`；浏览器 Origin 必须在白名单内 |
| 登录设备 | 每账号最多一个浏览器会话和一个桌面会话；同类新登录撤销旧会话，另一类继续有效 |
| 会话管理 | `listSessions`、`revokeSession`、`revokeOtherSessions` 均在 `/api/account` 下；退出仅撤销当前会话，改密与管理员强制下线撤销全部会话 |
| 文字重试 | `POST /api/chat/sendMessage` 可传 UUID `clientMessageId`；同发送者、同内容重试返回原消息，改变目标或内容拒绝 |
| 历史与已读 | `loadHistory` 按 `beforeMessageId` 游标读取，每页最多 50；`markRead` 单调推进本人游标，私聊 type 17 通知对端，群聊只维护本人未读 |
| 好友备注 | 最多 40 个 UTF-16 代码单元，仅本人可见；type 18 同步本人设备，见[备注契约](docs/contact-remarks.md) |
| 附件 | 先创建 type 5 占位，再上传文件；提交后发送 type 6，见[附件契约](docs/attachment-upload.md) |
| AI | 目标 `Urobot`；14/15/16 分别为初始化、累计全文片段和结束，终态 1/2/3 为完成/停止/失败 |

`POST /api/chat/cancelAiMessage` 只允许停止本人发起的回复，保存已生成正文。AI 正文使用 MEDIUMTEXT，会话摘要最多 500 个 Unicode 字符；普通输入仍最多 500 个 UTF-16 代码单元。同账号私聊回显、AI 提问和文件完成事件遵循[多端同步契约](docs/multi-device-message-sync.md)。

注册先通过图片验证码请求邮件，邮件中的 6 位验证码有效 10 分钟。默认限流包括：每邮箱邮件发送 3 次/小时、来源地址邮件请求 20 次/小时、注册 5 次/小时、邮件码校验 10 次/10 分钟、登录 10 次/10 分钟、每账号 WebSocket ticket 60 次/分钟、发送消息 120 次/分钟。Redis 不可用时相关限流请求失败关闭。

## Docker 与 NAS 部署用法

以下是待部署环境的操作说明，不表示本轮已经执行。持久化目录保存用户附件、头像及安装包；数据库与文件应一起备份。

**复用已有 MySQL/Redis：**复制 `.env.example` 为私有 `.env`，填写连接参数及 `WETALK_DATA_DIR`。Linux 宿主机的数据目录须允许容器 UID/GID `10001:10001` 写入。完成数据库迁移和 JAR 构建后：

```sh
docker compose -f compose.yaml config --quiet
docker compose -f compose.yaml up -d --build
docker compose -f compose.yaml ps
```

[compose.yaml](compose.yaml)默认将宿主机端口绑定到 `127.0.0.1`。可用 `BACKEND_BIND_IP`、`HTTP_PUBLISHED_PORT`、`WS_PUBLISHED_PORT` 调整；容器内部固定使用 5050/5051。`WETALK_DATA_DIR` 默认 `./data`。

**全新独立基础服务：**仅在新的部署目录使用 [prepare_infra.py](scripts/prepare_infra.py)。脚本生成随机凭据、`.env`、Redis 配置及数据目录，拒绝覆盖已有配置；不要先复制 `.env.example`。

```sh
python3 scripts/prepare_infra.py
sudo chown 999:999 config/redis.conf
sudo chown 10001:10001 data/backend
docker compose -f compose.infra.yaml config --quiet
docker compose -f compose.infra.yaml up -d
docker compose -f compose.infra.yaml ps
```

基础服务使用 MySQL 8.4、Redis 7.4 和 `wetalk-net`，宿主机默认回环端口 13306/16379。生成的后端 `.env` 使用网络内主机名，并将后端发布端口设为 15050/15051。待 MySQL/Redis 健康、配置好邮件与网页 Origin 后，再启动后端：

```sh
docker compose -f compose.yaml -f compose.nas.yaml config --quiet
docker compose -f compose.yaml -f compose.nas.yaml up -d --build
```

MySQL 只在空数据目录首次启动导入最新 `001-schema.sql`。已有库的 002–007 不会自动执行；不能清空数据目录来替代升级。反向代理需同源转发 `/api` 和 `/ws`，启用 WebSocket Upgrade；公网使用 HTTPS/WSS，并设置实际 Origin 和 Secure Cookie。

## 验证与发布产物

功能基线 `8bc5481` 的既有 Maven `clean verify` 记录为 **196 项通过、0 失败、0 错误、0 跳过**。这属于文档重写前的功能验收，本次纯 Markdown 修改没有重跑整套功能测试。测试源码位于 [src/test/java](src/test/java)，当前单元与组件测试不要求连接真实 MySQL/Redis。

构建后的后端发布包可由下列命令生成：

```sh
python scripts/package_release.py
```

脚本读取已有 `target/wetalk.jar` 与 Surefire 报告，生成 `dist/wetalk-backend-0.0.3-nas.zip` 及 `.zip.sha256`；包内有 JAR、Compose、Dockerfile、SQL、运维脚本、README/CHANGELOG、`RELEASE.json` 和 `SHA256SUMS`，不含 Docker 镜像、真实 `.env` 或业务数据。专题 `docs/` 当前不打入 ZIP，需要从源码仓库查阅。打包前应保证 JAR 与测试报告来自拟发布源码；脚本本身不重新编译测试。

部署验收至少包括就绪检查、实际邮箱注册、双端登录与会话撤销、私聊/群聊、幂等重试、未读/已读、附件字节与媒体 Range、备份恢复。生产域名和 HTTPS/WSS 尚未确定，本轮私有 QA 服务已停止，测试证据留在仓库外。

## 文档索引

- [更新日志](CHANGELOG.md)：版本历史和功能变化。
- [数据库初始化与升级](sql/README.md)：001 与 002–007 的适用范围、执行顺序和读回。
- [附件上传与读取](docs/attachment-upload.md)：占位、权限、配额、重试和 Range。
- [聊天图片格式](docs/chat-image-compatibility.md)：图片验证与 `.mjpeg` 别名。
- [私有好友备注](docs/contact-remarks.md)：字段、接口和 type 18。
- [多端消息同步](docs/multi-device-message-sync.md)：发送/接收视角、文件完成和 AI 事件。
- [版本发布管理](docs/app-releases.md)：草稿、灰度、全量和并发约束。
