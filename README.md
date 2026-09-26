# WeTalk 后端（0.0.2）

WeTalk 是 App 和 Web 共用的 Java 17 / Spring Boot 聊天服务。后端提供账号、联系人、群聊、消息历史、文件和管理员接口；MySQL 保存业务数据，Redis 保存会话/缓存并由 Redisson 广播消息，Netty 推送 WebSocket。AI 可选，默认关闭。

## 当前地址与协议

| 项目 | 默认地址/行为 |
|---|---|
| HTTP API | http://主机:5050/api |
| WebSocket | ws://主机:5051/ws?token=登录返回的token |
| 就绪检查 | GET /api/actuator/health/readiness |
| 健康检查 | GET /api/actuator/health |
| 登录态 | REST 请求带 token 请求头；失败业务码 901 |
| 返回值 | status、code、message、data；客户端按业务 code 判断结果 |
| 账号注册 | 邮箱、密码、昵称、图片验证码；目前没有邮箱归属验证 |
| AI | 默认不启用，无 AI 密钥也能启动普通聊天 |

生产反向代理应使用 HTTPS/WSS，并保持 /api、/ws 两个路径。健康接口仅返回 UP/DOWN，不公开配置或数据库详情；就绪检查同时检查 MySQL、Redis 和 WebSocket 是否成功绑定。

## 本次修复

详见 [CHANGELOG.md](CHANGELOG.md)。重点修复了私聊附件越权、群解散误断开连接、离线同步时间、过期连接清理、Redis 密码/数据库配置、旧 token 残留和首次部署配置。同时启用 Jakarta 参数校验，管理员预留邮箱不能经公开注册获得权限，API 返回的用户对象不再含密码摘要。

管理员账号须由可信的数据库初始化或现有账号配置完成，ADMIN_EMAILS 只填写已核实身份的邮箱；不能依靠公开注册创建管理员。已有配置曾包含真实连接信息时，需要在实际服务上更换相应凭据，当前文件改为环境变量不会清理 Git 历史。

## 运行条件

- JDK 17 或兼容的更新 JDK、Maven 3.9+。
- 可访问的 MySQL（数据库已建好，已有 WeTalk 表结构和所需初始数据）。
- 可访问的 Redis。Spring Redis 和 Redisson 共用 host、port、password、username、database、TLS 设置。
- 可写的文件目录，重启容器时必须保留。
- AI 使用 OpenAI 兼容接口时，还需要相应 provider/model/API Key。

**当前仓库没有官方数据库建表/初始化 SQL。** 本次没有猜测表结构或创建空 MySQL 容器。NAS 部署前须从正在使用的数据库或原毕设资料获得完整 schema/初始化数据并确认；否则“数据库连接成功”不代表注册和聊天可用。备份已有 MySQL 和文件目录后再做后续迁移。

本项目没有 Maven Wrapper，下面使用安装好的 mvn。

## 本地构建与测试

~~~shell
mvn clean verify
java -jar target/wetalk.jar
~~~

构建产物为 target/wetalk.jar（可执行 Spring Boot JAR）。不需要在构建机连接真实 MySQL/Redis：现有测试使用隔离替身和 Spring 小范围上下文，覆盖附件权限、参数校验、会话撤销、连接生命周期、启动失败和 AI 禁用配置。

~~~shell
python scripts/package_release.py
~~~

这会生成 dist/wetalk-backend-0.0.2-nas.zip 和对应 SHA256。它包含 JAR、Docker 构建文件、Compose、环境模板、文档、测试统计和内部文件校验清单；不包含真实 .env、数据目录、Git 历史或数据库备份。

## 环境变量

Spring Boot 不会自动读取本地 .env；本地直接运行 Java 时要由终端导出环境变量，Docker Compose 才会通过 env_file 读取它。

| 变量 | 默认/要求 |
|---|---|
| DB_URL | 默认 localhost:3306/easychat；NAS 上必须填真实 JDBC 地址 |
| DB_USERNAME / DB_PASSWORD | 专用数据库账号和密码；默认 wetalk / 空密码只作本地占位 |
| DB_POOL_SIZE | 10 |
| REDIS_HOST / REDIS_PORT | 127.0.0.1 / 6379；容器内 localhost 指容器本身 |
| REDIS_DATABASE | 0；复用现有 Redis 时选择合适的数据库，避免与其他 WeTalk 实例共用键 |
| REDIS_USERNAME / REDIS_PASSWORD | 可选 ACL 用户和 Redis 密码 |
| REDIS_SSL | false；启用时 Spring 和 Redisson 都使用 TLS |
| PROJECT_FOLDER | 本地 ./data/；Docker /data/wetalk/ |
| HTTP_PORT / WS_PORT | 5050 / 5051；Compose 固定内部端口 |
| ADMIN_EMAILS | 默认为空；可信的现有管理员邮箱，以逗号分隔 |
| MAX_UPLOAD_SIZE | 500MB，HTTP 与文件请求上限 |
| SPRING_PROFILES_ACTIVE | dev；Compose 设为 docker |
| WETALK_AI_ENABLED | false |
| WETALK_AI_MODEL | none；启用 AI 时设 openai |
| OPENAI_BASE_URL / OPENAI_MODEL / OPENAI_API_KEY | AI 启用时按提供商填写 |

启用 AI 需同时设置 WETALK_AI_ENABLED=true 和 WETALK_AI_MODEL=openai，再填写 model、base URL、API Key。普通聊天部署保持 false/none。此行为通过 Spring AI 自动配置测试验证，不使用伪造 API Key。

## NAS Docker 部署准备

这里是下一步部署操作说明，本次打包不等于已部署到 NAS。

1. 核对 NAS 架构、端口占用、已有 MySQL/Redis 地址、schema 和数据备份。建议独立目录 /volume2/docker/wetalk；这是建议路径，实际需现场核对。
2. 将发布 ZIP 传到 NAS，解压后核验文件校验和；包内保留 target/wetalk.jar 的相对结构。
3. 将 .env.example 复制为 .env 并填写真实连接参数。Compose 仅启动 WeTalk，不会改动或新建其他 MySQL、Redis 服务。
4. 创建持久化文件目录，让容器 UID/GID 10001 有读写权限。
5. 构建和启动，等待健康检查成功，再验证真实注册、登录、聊天、下载和 WebSocket。

~~~shell
cd /volume2/docker/wetalk
sha256sum -c SHA256SUMS
cp .env.example .env
# 编辑 .env，完成 DB/Redis/管理员等配置
mkdir -p data
# 使用 NAS 可用的管理员方式设置该专用目录权限
chown 10001:10001 data
docker compose config --quiet
docker compose build
docker compose up -d
docker compose ps
docker compose logs --tail=100 wetalk
curl -f http://127.0.0.1:5050/api/actuator/health/readiness
~~~

Compose 映射 5050 和 5051，可用 HTTP_PUBLISHED_PORT / WS_PUBLISHED_PORT 修改 NAS 侧端口。WETALK_DATA_DIR 控制持久化目录，建议采用经确认的绝对路径。容器非 root 运行，根文件系统只读，临时上传写 /tmp，正式文件写挂载目录。

Dockerfile 使用官方 [Eclipse Temurin](https://hub.docker.com/_/eclipse-temurin) Java 17 JRE 镜像，安装验证码所需字体和健康检查工具。构建需要网络获取基础镜像/系统包；如果 NAS 拉取受阻，可在有 Linux Docker 引擎的机器构建同架构镜像、docker save、校验传输后 docker load。当前发布 ZIP 是构建包，**不是 docker load 可导入的镜像 tar**。

## App / Web 连接 NAS

- Electron App 的 prodDomain 使用 http://NAS地址:5050（不附加 /api），prodWsDomain 使用 ws://NAS地址:5051/ws。当前客户端默认 localhost，部署后需改为真实地址并重新打包；本次没有改 App。
- 当前 WeTalkWeb 默认相对 /api，WebSocket 使用页面同源 /ws。开发时在 WeTalkWeb/vite.config.ts 将 /api 和 /ws 的代理目标改为 NAS HTTP/WS 地址。
- Web 的 VITE_API_BASE_URL 可配置 REST 基址，但 WebSocket 同源策略仍需 /ws 代理。生产建议反向代理统一域名，Web 静态页面、API 和 WebSocket 共用 HTTPS/WSS。
- 本后端没有添加任意 Origin 的全局 CORS 放行；用同源代理即可保留权限边界。
- 消息通过 POST /api/chat/sendMessage 发送，通过 WebSocket 接收。历史分页接口为 POST /api/chat/loadHistory，参数 contactId、beforeMessageId、pageSize（1..50）。
- 默认单账号单活跃连接；本次没有实现 App 与 Web 同账号同时在线。开发联调应使用两个不同测试账号，后续多端会话需要专门设计。

## 部署后必须验证

健康检查只验证依赖和端口，实际业务还须覆盖：
注册/图片验证码、登录、退出后复用旧 token 被拒绝、双账号互发消息、重连初始同步、私聊附件第三方下载被拒绝、群解散后其他会话仍在线，以及持久化文件重启后可读。启用 AI 时单独验证真实提供商的流式回答。

## 已知后续工作

- 当前密码协议仍兼容 App/Web 的历史 MD5 登录摘要；升级 BCrypt/Argon2 和邮箱验证必须协调客户端及已有账号迁移。
- 管理员角色仍按可信邮箱名单配置，后续宜加入数据库角色和邮箱验证。
- 客户端引用 /api/app/downloadUpdate，但当前后端没有该映射；桌面自动安装更新流程仍需补齐，普通聊天部署不依赖它。
- 网络 Redis/MySQL、NAS Docker 镜像运行和端到端 App/Web 尚须部署后验证。
